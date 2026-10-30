package app.charka.api;

import app.charka.check.CheckResult;
import app.charka.skill.SkillCheck;
import app.charka.skill.SkillService;
import lombok.RequiredArgsConstructor;
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
}
