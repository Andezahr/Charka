package app.charka.api;

import app.charka.check.CheckResult;
import app.charka.check.OpposedCheckResult;
import app.charka.skill.SkillCheck;
import app.charka.skill.SkillOpponent;
import app.charka.skill.SkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Проверка навыка из диалога страницы навыков: результат показывается на месте, без перезагрузки страницы.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/characters/{characterId}/skills")
public class SkillCheckController {

    private final SkillService skillService;

    /**
     * @param outcome короткий итог: «17 vs 15 — успех»
     * @param success успех; null, если сложность не задана
     */
    public record Response(String label, int total, String outcome, List<String> steps, Boolean success) {
    }

    /**
     * @param skillId строка навыка со своим названием; иначе навык определяется по {@code code}
     */
    @PostMapping("/check")
    public Response check(@PathVariable Long characterId,
                          @RequestParam(required = false) String code,
                          @RequestParam(required = false) Long skillId,
                          @RequestParam(defaultValue = "0") int modifier,
                          @RequestParam(required = false) Integer difficulty) {
        SkillCheck check = skillService.check(characterId, code, skillId, modifier, difficulty);
        CheckResult result = check.result();
        return new Response(check.label(), result.total(), result.outcome(), result.steps(), result.success());
    }

    /**
     * Навык для выбора в форме.
     *
     * @param skillId строка навыка со своим названием, иначе null
     * @param total   текущий стат + уровень
     */
    public record SkillOption(String code, Long skillId, String name, int level, int total) {
    }

    /**
     * Навыки персонажа, доступные для проверки: выбор навыка противника.
     */
    @GetMapping
    public List<SkillOption> skills(@PathVariable Long characterId) {
        return skillService.getSheet(characterId).groups().stream()
                .flatMap(group -> group.rows().stream())
                .map(row -> new SkillOption(row.code(), row.customId(), row.name(), row.level(), row.total()))
                .toList();
    }

    /**
     * @param winner  INITIATOR, OPPONENT или NONE
     * @param verdict итог: «Победа: Олежа (19 против 17)»
     */
    public record OpposedResponse(Response initiator, Response opponent, OpposedCheckResult.Winner winner, String verdict) {
    }

    /**
     * Противопоставленная проверка.
     *
     * @param skillId          строка навыка со своим названием; иначе навык определяется по {@code code}
     * @param opponentId       персонаж той же кампании; без него противник задаётся только именем и числом
     * @param opponentModifier модификатор персонажа либо «стат + навык» противника без листа
     */
    @PostMapping("/check/opposed")
    public OpposedResponse checkOpposed(@PathVariable Long characterId,
                                        @RequestParam(required = false) String code,
                                        @RequestParam(required = false) Long skillId,
                                        @RequestParam(defaultValue = "0") int modifier,
                                        @RequestParam(required = false) Long opponentId,
                                        @RequestParam(required = false) String opponentCode,
                                        @RequestParam(required = false) Long opponentSkillId,
                                        @RequestParam(required = false) String opponentName,
                                        @RequestParam(defaultValue = "0") int opponentModifier) {
        OpposedCheckResult result = skillService.checkOpposed(characterId, code, skillId, modifier,
                new SkillOpponent(opponentId, opponentCode, opponentSkillId, opponentName, opponentModifier));
        return new OpposedResponse(
                side(result.initiator(), result.winner() == OpposedCheckResult.Winner.INITIATOR),
                side(result.opponent(), result.winner() == OpposedCheckResult.Winner.OPPONENT),
                result.winner(), result.verdict());
    }

    private static Response side(OpposedCheckResult.Side side, boolean won) {
        CheckResult result = side.result();
        return new Response(side.title(), result.total(), result.outcome(), result.steps(), won);
    }
}
