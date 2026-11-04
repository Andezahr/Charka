package app.charka.journal;

import app.charka.check.CheckResult;
import app.charka.dice.HiddenRolls;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.JournalEntry;
import app.charka.model.RollLog;
import app.charka.model.counters.strategy.health.HealthEvent;
import app.charka.repository.JournalEntryRepository;
import app.charka.repository.RollLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Журнал боя (J-01): одно действие — одна запись с итогом, фишками и расчётом, чтобы за столом было видно,
 * что произошло: попал или нет, пробил ли броню, сколько урона, выдержал ли спасбросок.
 * <p>
 * Действие собирается «захватом» ({@link #capture}): всё, что внутри бросили, проверили и нанесли, едет с потоком
 * запроса (как {@link HiddenRolls}), а запись пишет только самый внешний захват. Так атака с проверкой, уроном и
 * спасброском цели — одна запись, а та же проверка навыка отдельно — своя.
 */
@Service
@RequiredArgsConstructor
public class JournalService {

    /** Вид записи */
    public enum Kind { ATTACK, CHECK, CONTEST, HEALTH, ROUND, TURN }

    /**
     * Фишка итога.
     *
     * @param kind вид для цвета: {@code hit}, {@code miss}, {@code ok}, {@code bad}, {@code dmg}, {@code armor},
     *             {@code pierce}, {@code dead}, {@code info}, {@code crit}
     */
    public record Chip(String kind, String text) {
    }

    /**
     * Что записать.
     *
     * @param total      итог главного броска; null — первый собранный бросок проверки
     * @param difficulty сложность; null — у первой собранной проверки
     */
    public record Draft(Campaign campaign, Character actor, Kind kind, String title, String outcome, Boolean success,
                        Integer total, Integer difficulty, List<String> steps, List<Chip> chips) {
    }

    private static final class Capture {
        private final List<RollLog> rolls = new ArrayList<>();
        private final List<CheckResult> checks = new ArrayList<>();
        private final List<Subject> health = new ArrayList<>();
    }

    private record Subject(String name, HealthEvent event) {
    }

    private static final ThreadLocal<Capture> CURRENT = new ThreadLocal<>();

    private final JournalEntryRepository journalEntryRepository;
    private final RollLogRepository rollLogRepository;

    /** Без записи: для юнит-тестов, где сервисы собираются руками */
    public static JournalService none() {
        return new JournalService(null, null);
    }

    /**
     * Выполнить действие и записать его одной записью. Внутри другого захвата — просто выполнить: запись будет одна,
     * у внешнего.
     *
     * @param describe что записать по итогу; null — ничего (собранные броски остаются в ленте сами по себе)
     */
    @Transactional
    public <T> T capture(Supplier<T> action, Function<T, Draft> describe) {
        if (CURRENT.get() != null) {
            return action.get();
        }
        Capture capture = new Capture();
        CURRENT.set(capture);
        T result;
        try {
            result = action.get();
        } finally {
            CURRENT.remove();
        }
        Draft draft = describe.apply(result);
        if (draft != null && draft.campaign() != null && journalEntryRepository != null) {
            record(draft, capture);
        }
        return result;
    }

    /** Бросок попал в лог: если идёт захват — он часть действия */
    public static void roll(RollLog roll) {
        Capture capture = CURRENT.get();
        if (capture != null) {
            capture.rolls.add(roll);
        }
    }

    /** Проверка по правилам системы внутри действия */
    public static void check(CheckResult result) {
        Capture capture = CURRENT.get();
        if (capture != null) {
            capture.checks.add(result);
        }
    }

    /** Урон, лечение или спасбросок персонажа внутри действия: его фишки попадут в запись */
    public static void health(String subject, HealthEvent event) {
        Capture capture = CURRENT.get();
        if (capture != null && event != null) {
            capture.health.add(new Subject(subject, event));
        }
    }

    private void record(Draft draft, Capture capture) {
        JournalEntry entry = new JournalEntry();
        entry.setCampaign(draft.campaign());
        entry.setCharacter(draft.actor());
        entry.setKind(draft.kind().name());
        entry.setTitle(truncate(draft.title(), 300));
        entry.setOutcome(draft.outcome() == null ? null : truncate(draft.outcome(), 1000));
        entry.setSuccess(draft.success());
        CheckResult first = capture.checks.isEmpty() ? null : capture.checks.get(0);
        entry.setTotal(draft.total() != null ? draft.total() : first == null ? null : first.total());
        entry.setDifficulty(draft.difficulty() != null ? draft.difficulty() : first == null || draft.total() != null ? null : first.difficulty());

        List<Chip> chips = new ArrayList<>(draft.chips());
        if (first != null && draft.kind() != Kind.CHECK) {
            if (first.fumble()) {
                chips.add(new Chip("bad", "Критический провал"));
            } else if (first.critical()) {
                chips.add(new Chip("crit", "Крит"));
            }
        }
        Set<String> subjects = capture.health.stream().map(Subject::name).collect(Collectors.toCollection(LinkedHashSet::new));
        boolean named = subjects.size() > 1 || draft.kind() == Kind.ATTACK || draft.kind() == Kind.ROUND;
        for (Subject subject : capture.health) {
            for (HealthEvent.Chip chip : subject.event().getChips()) {
                chips.add(new Chip(chip.kind(), (named ? subject.name() + ": " : "") + chip.text()));
            }
        }
        entry.setChips(truncate(chips.stream().map(chip -> chip.kind() + ":" + chip.text().replace('\n', ' '))
                .distinct().collect(Collectors.joining("\n")), 2000));
        entry.setSteps(String.join("\n", draft.steps().stream().map(step -> step.replace('\n', ' ')).toList()));
        entry.setHidden(HiddenRolls.isActive() || capture.rolls.stream().anyMatch(RollLog::isHidden));
        entry.setGameDate(draft.campaign().getCurrentDate());
        entry.setCreatedAt(LocalDateTime.now());
        journalEntryRepository.save(entry);
        capture.rolls.forEach(roll -> roll.setJournalEntryId(entry.getId()));
        rollLogRepository.saveAll(capture.rolls);
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
