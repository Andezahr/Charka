package app.charka.service.campaign;

import app.charka.exception.NotFoundException;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.CounterStrategy;
import app.charka.model.counters.strategy.CounterStrategyFactory;
import app.charka.repository.CampaignRepository;
import app.charka.skill.SkillProgression;
import app.charka.skill.SkillService;
import app.charka.skill.SkillSheet;
import app.charka.system.GameSystem;
import app.charka.system.GameSystemRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Начислить всем: опыт, очки улучшения. Счётчик выбирается по названию среди тех, что стратегия разрешает начислять
 * ({@link CounterStrategy#isAwardable()}).
 */
@Service
@RequiredArgsConstructor
public class CampaignAwardService {

    /** Сколько навыков назвать в подсказке «можно поднять» */
    private static final int HINTED_SKILLS = 3;

    private final CampaignRepository campaignRepository;
    private final CounterStrategyFactory strategyFactory;
    private final GameSystemRegistry systemRegistry;
    private final SkillService skillService;

    /**
     * Названия счётчиков, которые можно начислить персонажам кампании: сначала по порядку типов, потом по названию.
     */
    @Transactional(readOnly = true)
    public List<String> awardableCounters(Long campaignId) {
        return campaign(campaignId).getCharacters().stream()
                .flatMap(character -> character.getCounters().stream())
                .filter(this::isAwardable)
                .sorted(Comparator.comparingInt((Counter counter) -> strategyFactory.getStrategy(counter.getType()).getDisplayOrder())
                        .thenComparing(Counter::getName))
                .map(Counter::getName)
                .distinct()
                .toList();
    }

    /**
     * Прибавить {@code amount} к счётчику с этим названием у каждого выбранного персонажа кампании.
     * У кого счётчика нет, тот пропускается и называется в итоге.
     *
     * @return строка на персонажа: «Колясик: 0 → 10 · можно поднять: Пистолеты, Карате»
     * @throws IllegalArgumentException если никого не выбрали или прибавлять нечего
     */
    @Transactional
    public List<String> award(Long campaignId, String counterName, int amount, Collection<Long> characterIds) {
        if (characterIds == null || characterIds.isEmpty()) {
            throw new IllegalArgumentException("Выберите, кому начислить");
        }
        if (amount == 0) {
            throw new IllegalArgumentException("Укажите, сколько начислить");
        }
        Campaign campaign = campaign(campaignId);
        GameSystem system = systemRegistry.find(campaign.getSystem()).orElseGet(systemRegistry::getGeneric);
        List<String> lines = new ArrayList<>();
        for (Character character : campaign.getCharacters()) {
            if (!characterIds.contains(character.getId())) {
                continue;
            }
            Optional<Counter> found = character.getCounters().stream()
                    .filter(counter -> counter.getName().equals(counterName) && isAwardable(counter))
                    .findFirst();
            if (found.isEmpty()) {
                lines.add(character.getName() + ": нет счётчика «" + counterName + "»");
                continue;
            }
            Counter counter = found.get();
            Integer pointsBefore = points(system, character);
            int before = Objects.requireNonNullElse(counter.getValue(), 0);
            counter.setValue(Integer.valueOf(strategyFactory.getStrategy(counter.getType()).getNewValue(String.valueOf(before), amount)));
            lines.add(character.getName() + ": " + before + " → " + counter.getValue() + hint(system, character, pointsBefore));
        }
        return lines;
    }

    private Integer points(GameSystem system, Character character) {
        SkillProgression progression = system.getSkillProgression();
        return progression == null ? null : progression.balance(character);
    }

    /**
     * Подсветка порога: если начислены очки развития и теперь хватает на изученный навык — какие.
     */
    private String hint(GameSystem system, Character character, Integer pointsBefore) {
        Integer pointsAfter = points(system, character);
        if (pointsAfter == null || pointsAfter.equals(pointsBefore)) {
            return "";
        }
        List<String> affordable = skillService.getSheet(character.getId()).groups().stream()
                .flatMap(group -> group.rows().stream())
                .filter(row -> row.affordable() && row.level() > 0)
                .sorted(Comparator.comparing(SkillSheet.Row::nextCost))
                .map(SkillSheet.Row::name)
                .toList();
        if (affordable.isEmpty()) {
            return "";
        }
        String names = String.join(", ", affordable.subList(0, Math.min(HINTED_SKILLS, affordable.size())));
        return " · можно поднять: " + names + (affordable.size() > HINTED_SKILLS ? " и ещё " + (affordable.size() - HINTED_SKILLS) : "");
    }

    private boolean isAwardable(Counter counter) {
        return strategyFactory.findStrategy(counter.getType()).map(CounterStrategy::isAwardable).orElse(false);
    }

    private Campaign campaign(Long campaignId) {
        return campaignRepository.findById(campaignId).orElseThrow(NotFoundException.forId("Campaign", campaignId));
    }
}
