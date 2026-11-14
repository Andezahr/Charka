package app.charka.system.kosmozeki;

import app.charka.skill.CharacterRole;
import java.util.List;
import app.charka.skill.SkillRules;

/**
 * ARC-01: навыки, роли, прокачка и текущие статы — правила этой системы (вынесено из KzSystem).
 */
class KzSkillRules implements SkillRules {

    @Override
    public List<CharacterRole> getRoles() {
        return KzSystem.ROLES;
    }
}
