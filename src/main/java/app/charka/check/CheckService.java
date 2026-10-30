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

    /**
     * Противопоставленная проверка: оба бросают по правилам системы инициатора, у каждого своя запись в логе.
     *
     * @param opponent        противник из той же кампании; null — противник без листа (мастер вводит только число)
     * @param opponentName    имя противника без листа; у противника с листом не используется
     * @param opponentRequest бросок противника, сложность в обоих запросах не учитывается
     */
    @Transactional
    public OpposedCheckResult checkOpposed(Character initiator, CheckRequest request,
                                           Character opponent, String opponentName, CheckRequest opponentRequest) {
        if (opponent != null) {
            requireSameCampaign(initiator, opponent);
        }
        CheckResolver resolver = systemRegistry.forCharacter(initiator).getCheckResolver();
        CheckResult own = resolver.resolve(withoutDifficulty(request), diceRoller);
        CheckResult other = resolver.resolve(withoutDifficulty(opponentRequest), diceRoller);
        OpposedCheckResult result = new OpposedCheckResult(
                new OpposedCheckResult.Side(initiator.getName(), request.label(), own),
                new OpposedCheckResult.Side(opponent != null ? opponent.getName() : opponentName, opponent != null ? opponentRequest.label() : null, other),
                resolver.opposedWinner(own, other), null);
        result = new OpposedCheckResult(result.initiator(), result.opponent(), result.winner(), verdict(result));

        logOpposed(initiator.getCampaign(), initiator, result.initiator(), result.opponent().name(), result.verdict());
        // противника без листа пишем как бросок мастера: вне кампании такой записи негде быть
        if (opponent != null || initiator.getCampaign() != null) {
            logOpposed(initiator.getCampaign(), opponent, result.opponent(), initiator.getName(), result.verdict());
        }
        return result;
    }

    private static void requireSameCampaign(Character initiator, Character opponent) {
        if (opponent.getId().equals(initiator.getId())) {
            throw new IllegalArgumentException("Нельзя проверять персонажа против него самого");
        }
        boolean sameCampaign = initiator.getCampaign() != null && opponent.getCampaign() != null
                && initiator.getCampaign().getId().equals(opponent.getCampaign().getId());
        if (!sameCampaign) {
            throw new IllegalArgumentException("Противник должен быть из той же кампании");
        }
    }

    private static CheckRequest withoutDifficulty(CheckRequest request) {
        return new CheckRequest(request.label(), request.modifier(), null, request.parts(), request.skillCode());
    }

    private static String verdict(OpposedCheckResult result) {
        OpposedCheckResult.Side initiator = result.initiator();
        OpposedCheckResult.Side opponent = result.opponent();
        if (result.winner() == OpposedCheckResult.Winner.NONE) {
            return "Оба критически провалились — победителя нет";
        }
        boolean initiatorWon = result.winner() == OpposedCheckResult.Winner.INITIATOR;
        OpposedCheckResult.Side winner = initiatorWon ? initiator : opponent;
        OpposedCheckResult.Side loser = initiatorWon ? opponent : initiator;
        if (loser.result().fumble()) {
            return "Критический провал: " + loser.name() + ". Победа: " + winner.name();
        }
        if (initiator.result().total() == opponent.result().total()) {
            return "Равенство " + winner.result().total() + " — победа защищающегося: " + winner.name();
        }
        return "Победа: " + winner.name() + " (" + winner.result().total() + " против " + loser.result().total() + ")";
    }

    private void logOpposed(Campaign campaign, Character character, OpposedCheckResult.Side side,
                            String against, String verdict) {
        String details = String.join("; ", side.result().steps()) + "; " + verdict;
        String label = (side.skill() != null ? side.skill() : side.name()) + " vs " + against;
        diceService.log(campaign, character, side.result().notation(), details, side.result().total(), label);
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
