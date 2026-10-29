package app.charka.dice;

import app.charka.exception.CharacterNotFoundException;
import app.charka.exception.NotFoundException;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.RollLog;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.repository.RollLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Броски кубиков по выражению ({@link DiceExpression}) и их лог.
 */
@Service
@RequiredArgsConstructor
public class DiceService {

    private final DiceRoller diceRoller;
    private final RollLogRepository rollLogRepository;
    private final CharacterRepository characterRepository;
    private final CampaignRepository campaignRepository;

    /**
     * Бросок без записи в лог.
     *
     * @throws IllegalArgumentException если выражение не разобрано
     */
    public DiceResult roll(String expression) {
        return roll(DiceExpression.parse(expression));
    }

    public DiceResult roll(DiceExpression expression) {
        List<Integer> rolls = new ArrayList<>(expression.count());
        for (int i = 0; i < expression.count(); i++) {
            rolls.add(diceRoller.roll(expression.sides()));
        }
        return DiceResult.of(expression, rolls);
    }

    /**
     * Бросок от имени персонажа, пишется в лог его кампании.
     *
     * @param label подпись броска, может быть пустой
     */
    @Transactional
    public DiceResult rollForCharacter(Long characterId, String expression, String label) {
        Character character = characterRepository.findById(characterId)
                .orElseThrow(CharacterNotFoundException.forId(characterId));
        DiceResult result = roll(expression);
        log(character.getCampaign(), character, result, label);
        return result;
    }

    /**
     * Бросок мастера в кампании (без персонажа).
     */
    @Transactional
    public DiceResult rollForCampaign(Long campaignId, String expression, String label) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(NotFoundException.forId("Campaign", campaignId));
        DiceResult result = roll(expression);
        log(campaign, null, result, label);
        return result;
    }

    /**
     * Последние броски кампании, новые сверху.
     */
    @Transactional(readOnly = true)
    public List<RollLog> recentForCampaign(Long campaignId, int limit) {
        return rollLogRepository.findByCampaignIdOrderByCreatedAtDescIdDesc(campaignId, PageRequest.of(0, limit));
    }

    /**
     * Последние броски персонажа, новые сверху.
     */
    @Transactional(readOnly = true)
    public List<RollLog> recentForCharacter(Long characterId, int limit) {
        return rollLogRepository.findByCharacterIdOrderByCreatedAtDescIdDesc(characterId, PageRequest.of(0, limit));
    }

    private void log(Campaign campaign, Character character, DiceResult result, String label) {
        RollLog entry = new RollLog();
        entry.setCampaign(campaign);
        entry.setCharacter(character);
        entry.setExpression(result.expression().toString());
        entry.setDetails(result.details());
        entry.setTotal(result.total());
        entry.setLabel(label == null || label.isBlank() ? null : label.strip());
        entry.setGameDate(campaign == null ? null : campaign.getCurrentDate());
        entry.setCreatedAt(LocalDateTime.now());
        rollLogRepository.save(entry);
    }
}
