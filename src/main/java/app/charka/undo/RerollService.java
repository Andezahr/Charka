package app.charka.undo;

import app.charka.exception.CharacterNotFoundException;
import app.charka.model.Character;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.InspirationCounterStrategy;
import app.charka.repository.CharacterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * K-09 [CORE]: переброс за вдохновение. После атаки, проверки или броска персонажа плашка отмены предлагает «↻ Перебросить»:
 * действие отменяется (A-01, если оно меняло игру), вдохновение −1, тот же запрос выполняется ещё раз — с новыми кубиками.
 */
@Service
@RequiredArgsConstructor
public class RerollService {

    public static final String OFFER = "rerollOffer";

    /** Что можно перебросить: атака, проверка, бросок персонажа — путь после {@code /character/{id}} */
    private static final Pattern REROLLABLE = Pattern.compile("^/(items/\\d+/attack|attack/ability/[A-Z_]+|check|roll)$");

    private final CharacterRepository characterRepository;
    private final UndoService undoService;

    /**
     * Предложение переброса.
     *
     * @param characterId чей бросок
     * @param entryId     запись отмены этого действия; null — действие игру не меняло (проверка, бросок)
     * @param path        адрес действия
     * @param params      его поля
     * @param left        вдохновения осталось
     */
    public record Offer(Long characterId, Long entryId, String path, List<Param> params, int left) {
    }

    public record Param(String name, String value) {
    }

    public static boolean isRerollable(String rest) {
        return rest != null && REROLLABLE.matcher(rest).matches();
    }

    /** Сколько вдохновения у персонажа; пусто — счётчика нет */
    @Transactional(readOnly = true)
    public Optional<Integer> inspiration(Long characterId) {
        return characterRepository.findById(characterId).flatMap(RerollService::counter).map(counter -> counter.getValue() == null ? 0 : counter.getValue());
    }

    private static Optional<Counter> counter(Character character) {
        return character.getCounters().stream().filter(counter -> InspirationCounterStrategy.TYPE.equals(counter.getType())).findFirst();
    }

    /**
     * Отменить действие и потратить вдохновение; дальше контроллер повторяет запрос.
     *
     * @return адрес, который повторить
     * @throws IllegalArgumentException вдохновения нет, адрес не тот или действие уже не последнее
     */
    @Transactional
    public String prepare(Long characterId, Long entryId, String path) {
        String prefix = "/character/" + characterId;
        if (path == null || !path.startsWith(prefix) || !isRerollable(path.substring(prefix.length()))) {
            throw new IllegalArgumentException("Это не перебросить");
        }
        if (entryId != null) {
            Long campaignId = undoService.campaignOf(characterId);
            undoService.undo(campaignId, entryId);
        }
        Character character = characterRepository.findById(characterId).orElseThrow(CharacterNotFoundException.forId(characterId));
        Counter inspiration = counter(character).filter(counter -> counter.getValue() != null && counter.getValue() > 0)
                .orElseThrow(() -> new IllegalArgumentException("Вдохновения нет: его выдаёт мастер"));
        inspiration.setValue(inspiration.getValue() - 1);
        characterRepository.save(character);
        return path;
    }

    /** Поля запроса для повтора: без токена */
    public static List<Param> params(Map<String, String[]> parameters) {
        return parameters.entrySet().stream()
                .filter(entry -> !entry.getKey().equals("_csrf"))
                .flatMap(entry -> java.util.Arrays.stream(entry.getValue()).map(value -> new Param(entry.getKey(), value)))
                .toList();
    }
}
