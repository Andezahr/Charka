package app.charka.skill;

import app.charka.check.CheckResult;

/**
 * Итог проверки навыка вместе с его названием (подпись результата на странице).
 */
public record SkillCheck(String label, CheckResult result) {
}
