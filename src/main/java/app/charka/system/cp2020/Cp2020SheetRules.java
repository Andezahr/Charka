package app.charka.system.cp2020;

import app.charka.attack.AttackRules;
import app.charka.attack.AttackSource;
import app.charka.check.CheckRequest;
import app.charka.check.CheckResolver;
import app.charka.encounter.CombatCard;
import app.charka.encounter.InitiativeDeclaration;
import app.charka.item.InstalledBlock;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.encounter.Initiative;
import app.charka.item.CatalogItem;
import app.charka.item.ItemAction;
import app.charka.item.ItemAttachments;
import app.charka.item.ItemSummary;
import app.charka.item.ItemTypeDefinition;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.CharacterSkill;
import app.charka.model.Item;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.CounterContext;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.npc.NpcSheet;
import app.charka.npc.NpcTemplate;
import app.charka.service.counter.HealthCounterService;
import app.charka.skill.CharacterRole;
import app.charka.skill.SkillDefinition;
import app.charka.skill.SkillProgression;
import app.charka.skill.StatValue;
import app.charka.system.Currency;
import app.charka.system.GameSystem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import app.charka.system.SheetRules;

/**
 * ARC-01: лист персонажа: поля профиля, строка здоровья, заголовок контактов, лист на бумаге — правила этой системы (вынесено из Cp2020System).
 */
class Cp2020SheetRules implements SheetRules {

    private final Cp2020System system;

    Cp2020SheetRules(Cp2020System system) {
        this.system = system;
    }

    @Override
    public String getHealthStatus(Character character) {
        return character.getCounters().stream()
                .filter(counter -> Cp2020HealthStrategy.TYPE.equals(counter.getType()))
                .findFirst()
                .map(Cp2020System::healthBrief)
                .orElse(null);
    }

    /** KF-07: в книге — Contacts: фиксеры, копы, бывшие */
    @Override
    public String getContactsTitle() {
        return "Контакты";
    }

    @Override
    public app.charka.system.PrintSheet getPrintSheet(Character character) {
        return Cp2020Print.sheet(character, system.skills().getCurrentStats(character), Cp2020System.ARMOR);
    }
}
