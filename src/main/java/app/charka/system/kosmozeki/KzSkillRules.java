package app.charka.system.kosmozeki;

import app.charka.attack.AttackRules;
import app.charka.check.CheckResolver;
import app.charka.encounter.CombatCard;
import app.charka.encounter.Initiative;
import app.charka.item.CatalogItem;
import app.charka.item.ItemAction;
import app.charka.item.ItemAttachments;
import app.charka.item.ItemSummary;
import app.charka.item.ItemTypeDefinition;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.InspirationCounterStrategy;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.npc.NpcTemplate;
import app.charka.service.counter.HealthCounterService;
import app.charka.skill.CharacterRole;
import app.charka.system.Currency;
import app.charka.system.GameSystem;
import app.charka.system.ProfileField;
import app.charka.system.Reference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
