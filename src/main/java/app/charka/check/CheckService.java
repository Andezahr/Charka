package app.charka.check;

import app.charka.dice.DiceService;
import app.charka.exception.CharacterNotFoundException;
import app.charka.exception.NotFoundException;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.system.GameSystemRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Проверки «бросок + модификатор против сложности» по правилам системы кампании; каждая пишется в лог бросков.
 */
@Service
@RequiredArgsConstructor
public class CheckService {

    private static final String DEFAULT_LABEL = "Проверка";

    private final GameSystemRegistry systemRegistry;
    private final DiceRoller diceRoller;
    private final DiceService diceService;
    private final CharacterRepository characterRepository;
    private final CampaignRepository campaignRepository;

    /**
     * Проверка персонажа по правилам системы его кампании.
     */
    @Transactional
    public CheckResult checkForCharacter(Long characterId, CheckRequest request) {
        Character character = characterRepository.findById(characterId)
                .orElseThrow(CharacterNotFoundException.forId(characterId));
        CheckResult result = systemRegistry.forCharacter(character).getCheckResolver().resolve(request, diceRoller);
        log(character.getCampaign(), character, request, result);
        return result;
    }

    /**
     * Проверка мастера (без персонажа) по правилам системы кампании.
     */
    @Transactional
    public CheckResult checkForCampaign(Long campaignId, CheckRequest request) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(NotFoundException.forId("Campaign", campaignId));
        CheckResult result = systemRegistry.find(campaign.getSystem()).orElseGet(systemRegistry::getGeneric)
                .getCheckResolver().resolve(request, diceRoller);
        log(campaign, null, request, result);
        return result;
    }

    private void log(Campaign campaign, Character character, CheckRequest request, CheckResult result) {
        String expression = result.difficulty() == null
                ? result.notation()
                : result.notation() + " vs " + result.difficulty();
        String details = String.join("; ", result.steps());
        String label = request.label() == null || request.label().isBlank() ? DEFAULT_LABEL : request.label();
        diceService.log(campaign, character, expression, details, result.total(), label);
    }
}
