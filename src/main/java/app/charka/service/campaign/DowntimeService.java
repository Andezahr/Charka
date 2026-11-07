package app.charka.service.campaign;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Money;
import app.charka.model.counters.Counter;
import app.charka.service.MoneyService;
import app.charka.service.counter.HealthCounterService;
import app.charka.system.Earning;
import app.charka.system.GameSystem;
import app.charka.system.GameSystemRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Даунтайм одной формой (A-10): «прошла неделя» — кто работал, кого лечит какой медик, и промотка времени одной записью
 * хроники. Применяется сразу, а итог по каждому персонажу показан с «↶ Отменить и изменить» (отмена A-01): так то,
 * что видит мастер, — ровно то, что случилось, с теми же бросками.
 */
@Service
@RequiredArgsConstructor
public class DowntimeService {

    private final CampaignService campaignService;
    private final CampaignTimelineService timelineService;
    private final GameSystemRegistry systemRegistry;
    private final HealthCounterService healthCounterService;
    private final MoneyService moneyService;

    /**
     * Строка формы.
     *
     * @param earning   заработок за месяц, если работал; null — работы нет
     * @param wounded   есть урон: можно выбрать медика
     * @param healers   кто может лечить, лучший медик первым
     * @param healer    кого предложить: лучший медик, если у него есть навык; null — без медика
     * @param rests     K-08: как можно отдыхать (Космозэки); пусто — выбора нет, вместо него медик
     * @param rest      что выбрано сейчас
     */
    public record Row(Character character, Earning earning, boolean wounded, List<Character> healers, Long healer,
                      List<app.charka.item.ItemField.Option> rests, String rest) {
    }

    @Transactional(readOnly = true)
    public List<Row> form(Long campaignId) {
        Campaign campaign = campaignService.getById(campaignId);
        GameSystem system = systemRegistry.get(campaign.getSystem());
        return campaign.getCharacters().stream()
                .sorted(Comparator.comparing(Character::isNpc).thenComparing(Character::getName))
                .map(character -> {
                    Counter health = healthCounterService.healthCounter(character.getId()).orElse(null);
                    boolean wounded = health != null && health.getValue() != null && health.getValue() > 0
                            && !system.getCombatCard(character).out();
                    List<Character> healers = wounded ? healthCounterService.healers(character.getId(), List.of()) : List.of();
                    Long healer = wounded ? bestHealer(character, healers, health) : null;
                    List<app.charka.item.ItemField.Option> rests = health == null ? List.of() : healthCounterService.restOptions(health);
                    return new Row(character, system.earnings(character, 30), wounded, rests.isEmpty() ? healers : List.of(),
                            healer, rests, health == null ? null : healthCounterService.rest(health));
                })
                .toList();
    }

    /** Лучший медик, у кого есть навык для нужной сейчас помощи (себе — если система позволяет); никого — без медика */
    private Long bestHealer(Character patient, List<Character> healers, Counter health) {
        var options = healthCounterService.careOptions(patient.getId(), health.getId());
        for (Character healer : healers) {
            String code = healthCounterService.defaultCare(patient.getId(), health.getId(), healer);
            var option = options.stream().filter(candidate -> candidate.code().equals(code)).findFirst().orElse(null);
            if (option == null || (healer.getId().equals(patient.getId()) && !option.selfAllowed())) {
                continue;
            }
            if (healer.getSkills().stream().anyMatch(skill -> skill.getLevel() > 0 && skill.getSkillCode().equals(option.skillCode()))) {
                return healer.getId();
            }
        }
        return null;
    }

    /**
     * Применить: медпомощь (бросок медика), заработок тем, кто работал, и промотка времени (лечение, траты, эффекты, чипы).
     *
     * @param worked  кто работал
     * @param healers пациент → медик
     * @return итог по шагам
     * @throws IllegalArgumentException дней меньше 1 или больше года
     */
    @Transactional
    public List<String> apply(Long campaignId, int days, String name, Set<Long> worked, Map<Long, Long> healers) {
        return apply(campaignId, days, name, worked, healers, Map.of());
    }

    /**
     * @param rests K-08: персонаж → как отдыхал (Космозэки: сон, сон и еда); запоминается и лечит при промотке
     */
    @Transactional
    public List<String> apply(Long campaignId, int days, String name, Set<Long> worked, Map<Long, Long> healers, Map<Long, String> rests) {
        if (days < 1 || days > 366) {
            throw new IllegalArgumentException("Дней — от 1 до 366");
        }
        Campaign campaign = campaignService.getById(campaignId);
        GameSystem system = systemRegistry.get(campaign.getSystem());
        List<String> report = new ArrayList<>();
        rests.forEach(healthCounterService::setRest);
        for (Character character : campaign.getCharacters()) {
            Long healerId = healers.get(character.getId());
            if (healerId == null) {
                continue;
            }
            healthCounterService.healthCounter(character.getId()).ifPresent(health -> {
                Character healer = campaign.getCharacters().stream().filter(c -> c.getId().equals(healerId)).findFirst().orElse(null);
                String option = healthCounterService.defaultCare(character.getId(), health.getId(), healer);
                if (healer == null || option == null) {
                    return;
                }
                try {
                    report.add(character.getName() + ": " + healthCounterService.care(character.getId(), health.getId(), healerId, option, 0).getSummary());
                } catch (IllegalArgumentException e) {
                    report.add(character.getName() + ": медпомощь не вышла — " + e.getMessage());
                }
            });
        }
        for (Character character : campaign.getCharacters()) {
            if (!worked.contains(character.getId())) {
                continue;
            }
            Earning earning = system.earnings(character, days);
            if (earning == null || earning.amount() <= 0) {
                continue;
            }
            Money money = new Money();
            money.setName(earning.label());
            money.setAmount(earning.amount());
            money.setOperationDate(campaign.getCurrentDate().plusDays(days));
            moneyService.create(character.getId(), money);
            report.add(character.getName() + ": " + earning.label() + " +" + earning.amount() + " " + system.getCurrency().symbol());
        }
        String chronicle = name == null || name.isBlank() ? "Прошло " + days + " дн." : name.strip();
        report.addAll(timelineService.proceedDate(campaignId, days, chronicle));
        return report;
    }
}
