package app.charka.attack;

import app.charka.encounter.EncounterService;
import app.charka.encounter.EncounterView;
import app.charka.item.CatalogItem;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.service.CharacterService;
import app.charka.service.ItemService;
import app.charka.skill.SkillService;
import app.charka.system.GameSystemRegistry;
import app.charka.system.cp2020.Cp2020HealthStrategy;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/** REF 6 у всех, BODY 6: прибавки к урону нет, BTM 2 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Ближний бой: весь ход на настоящих сервисах")
class MeleeAttackTest {

    @Autowired private AttackService attackService;
    @Autowired private EncounterService encounterService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private CharacterService characterService;
    @Autowired private ItemService itemService;
    @Autowired private SkillService skillService;
    @Autowired private GameSystemRegistry systemRegistry;
    @Autowired private EntityManager entityManager;

    @MockitoBean(answers = Answers.CALLS_REAL_METHODS) private DiceRoller dice;

    private Campaign campaign;
    private Character attacker;
    private Character victim;

    @BeforeEach
    void setUp() {
        Campaign created = new Campaign();
        created.setName("Тест");
        created.setSystem("CP2020");
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        campaign = campaignRepository.save(created);
        attacker = member("Боец");
        victim = member("Цель");
        flush();
    }

    private Character member(String name) {
        Character character = new Character();
        character.setName(name);
        return characterService.createInCampaign(campaign.getId(), character);
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }

    private Item give(Character owner, String key) {
        Long inventory = characterRepository.findById(owner.getId()).orElseThrow().getInventories().get(0).getId();
        return itemService.addFromCatalog(inventory, key, 1);
    }

    private void wear(String key) {
        Item armor = give(victim, key);
        itemService.performAction(victim.getId(), armor.getId(), "WEAR", Map.of());
        flush();
    }

    private void skill(Character owner, String code, int level) {
        skillService.updateLevels(owner.getId(), Map.of(code, level), Map.of());
        flush();
    }

    private int damage() {
        Counter health = characterRepository.findById(victim.getId()).orElseThrow().getCounters().stream()
                .filter(counter -> Cp2020HealthStrategy.TYPE.equals(counter.getType()))
                .findFirst().orElseThrow();
        return health.getValue();
    }

    private AttackRequest at(String mode, Set<String> options) {
        return new AttackRequest(List.of(victim.getId()), null, 0, "TORSO", null, options, 0, mode, 0, 0);
    }

    @Test
    @DisplayName("Встречный бросок: защита 7, удар проходит на 8; выбор зоны −4")
    void opposed() {
        Item club = give(attacker, "club");
        // защита d10 1, удар d10 6, урон 6
        when(dice.roll(anyInt())).thenReturn(1, 6, 6);

        AttackResult result = attackService.attack(attacker.getId(), club.getId(), at("MELEE", Set.of()));
        flush();

        assertThat(result.steps()).anyMatch(step -> step.startsWith("Защита Цель") && step.contains("равенство за защитой"));
        // 6 + 0 + 0 − 4 + 6 = 8 против 7 + 1: без стиля ключевых приёмов нет
        assertThat(result.hit()).isTrue();
        assertThat(damage()).isEqualTo(4);
    }

    @Test
    @DisplayName("Равенство за защитой: 7 против 7 — промах")
    void tieGoesToDefender() {
        Item club = give(attacker, "club");
        when(dice.roll(anyInt())).thenReturn(1, 5);

        AttackResult result = attackService.attack(attacker.getId(), club.getId(), at("MELEE", Set.of()));

        assertThat(result.hit()).isFalse();
        assertThat(damage()).isZero();
    }

    @Test
    @DisplayName("Клинок: лёгкая куртка с отметкой √ считается за половину SP; средняя — полностью")
    void blade() {
        wear("armor_light_jacket");
        Item sword = give(attacker, "sword");
        // защита 1; удар 10 и 1; урон 6 + 6 + 2 = 14; SP 14 → 7
        when(dice.roll(anyInt())).thenReturn(1, 10, 1, 6, 6);

        AttackResult result = attackService.attack(attacker.getId(), sword.getId(), at("MELEE", Set.of()));
        flush();

        assertThat(result.steps()).anyMatch(step -> step.contains("считается за 7"));
        assertThat(damage()).isEqualTo(14 - 7 - 2);
    }

    @Test
    @DisplayName("Моно-лезвие: мягкая броня ⅓, на естественной 10 урон вдвое (с. 112)")
    void mono() {
        wear("armor_heavy_jacket");
        Item katana = give(attacker, "kendachi_monokatana");
        // защита 1; удар 10 и 1; урон 4 × 3 = 12, ×2 = 24; SP 20 → 6
        when(dice.roll(anyInt())).thenReturn(1, 10, 1, 3);

        AttackResult result = attackService.attack(attacker.getId(), katana.getId(), at("MELEE", Set.of()));
        flush();

        assertThat(result.steps()).anyMatch(step -> step.contains("удваивает"));
        assertThat(damage()).isEqualTo(24 - 6 - 2);
    }

    @Test
    @DisplayName("Моно-лезвие на фамбле ломается всегда (с. 112)")
    void monoBreaks() {
        Item knife = give(attacker, "kendachi_monoknife");
        flush();
        when(dice.roll(anyInt())).thenReturn(1);

        AttackResult result = attackService.attack(attacker.getId(), knife.getId(), at("MELEE", Set.of()));
        flush();

        assertThat(result.hit()).isFalse();
        assertThat(result.detached()).isTrue();
        assertThat(characterRepository.findById(attacker.getId()).orElseThrow().getInventories().get(0).getContent())
                .noneMatch(item -> item.getName().equals("Kendachi Monoknife"));
    }

    @Test
    @DisplayName("Уклонение −2 к атаке; парирование: урон в предмет, цель цела")
    void defenses() {
        Item club = give(attacker, "club");
        when(dice.roll(anyInt())).thenReturn(1, 7, 6);

        AttackResult dodged = attackService.attack(attacker.getId(), club.getId(), at("MELEE", Set.of("target_dodges")));
        assertThat(dodged.steps()).anyMatch(step -> step.contains("Цель уклоняется"));

        int before = damage();
        when(dice.roll(anyInt())).thenReturn(1, 9, 6);
        AttackResult parried = attackService.attack(attacker.getId(), club.getId(), at("MELEE", Set.of("target_parries")));

        assertThat(parried.outcome()).contains("парирован");
        assertThat(damage()).isEqualTo(before);
    }

    @Test
    @DisplayName("Кулаки: каратэ сам становится лучшим навыком, ключевой приём и уровень в уроне")
    void brawl() {
        skill(attacker, "karate", 3);
        // защита 1; пинок 5; урон 4: 4 + каратэ 3 = 7
        when(dice.roll(anyInt())).thenReturn(1, 5, 4);

        AttackResult result = attackService.attackWithAbility(attacker.getId(), "BRAWL", at("KICK", Set.of()));
        flush();

        assertThat(result.detached()).isTrue();
        assertThat(result.steps()).anyMatch(step -> step.contains("Ключевой приём")).anyMatch(step -> step.contains("Карате: урон +3"));
        assertThat(damage()).isEqualTo(7 - 2);
    }

    @Test
    @DisplayName("Удар кулаком 1D6/2 не меньше 1, защита берёт лучший навык цели")
    void strikeMinimum() {
        skill(victim, "dodge_escape", 2);
        when(dice.roll(anyInt())).thenReturn(1, 9, 1);

        AttackResult result = attackService.attackWithAbility(attacker.getId(), "BRAWL", at("STRIKE", Set.of()));
        flush();

        assertThat(result.steps()).anyMatch(step -> step.contains("Уклонение и освобождение 2"));
        // 1D6/2 = 1, BTM −2, минимум 1
        assertThat(damage()).isEqualTo(1);
    }

    @Test
    @DisplayName("Нож и бьёт, и летит: удар не тратит предмет, бросок тратит")
    void knifeBothWays() {
        Item knife = give(attacker, "knife");
        when(dice.roll(anyInt())).thenReturn(1, 9, 3);
        attackService.attack(attacker.getId(), knife.getId(), at("MELEE", Set.of()));
        flush();
        assertThat(characterRepository.findById(attacker.getId()).orElseThrow().getInventories().get(0).getContent())
                .anyMatch(item -> item.getName().equals("Knife"));
    }

    @Test
    @DisplayName("В бою у участника есть рукопашная и холодное оружие")
    void inTracker() {
        give(attacker, "sword");
        flush();
        encounterService.start(campaign.getId(), List.of(attacker.getId(), victim.getId()));
        flush();

        EncounterView.Row row = encounterService.view(campaign.getId()).orElseThrow().rows().stream()
                .filter(candidate -> candidate.character().getId().equals(attacker.getId())).findFirst().orElseThrow();

        assertThat(row.abilities()).extracting(EncounterView.Ability::name).containsExactly("Рукопашная");
        assertThat(row.weapons()).extracting(weapon -> weapon.item().getName()).contains("Sword");
    }

    @Test
    @DisplayName("Неизвестная способность и две цели отклоняются")
    void refused() {
        assertThatThrownBy(() -> attackService.attackWithAbility(attacker.getId(), "FIREBALL", at("STRIKE", Set.of())))
                .isInstanceOf(IllegalArgumentException.class);
        Character third = member("Третий");
        flush();
        assertThatThrownBy(() -> attackService.attackWithAbility(attacker.getId(), "BRAWL",
                new AttackRequest(List.of(victim.getId(), third.getId()), null, 0, null, null, Set.of(), 0, "STRIKE", 0, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Пули к дробовику: одна цель, урон по калибру, SP вдвое, через мягкую броню урон делится (с. 109)")
    void slugs() {
        wear("armor_light_jacket");
        Item gun = give(attacker, "arasaka_rapid_assault_12");
        Item box = give(attacker, "ammo_slugs");
        flush();
        Item full = characterRepository.findById(attacker.getId()).orElseThrow().getInventories().get(0).getContent().stream()
                .filter(item -> item.getId().equals(gun.getId())).findFirst().orElseThrow();
        // магазин полон дроби, а пули всё равно предлагаются: другим видом его можно сменить
        assertThat(systemRegistry.forCharacter(full.getInventory().getCharacter()).getItemActions(full))
                .filteredOn(action -> action.code().equals("RELOAD")).singleElement().satisfies(action -> {
                    assertThat(action.sources()).extracting(option -> option.value()).contains(String.valueOf(box.getId()));
                    assertThat(action.isShownInCombat()).isTrue();
                });
        // Магазин снят, в него идут пули: прежняя дробь уходит в запас
        itemService.performAction(attacker.getId(), gun.getId(), "RELOAD", Map.of("source", String.valueOf(box.getId())));
        flush();
        Item loaded = characterRepository.findById(attacker.getId()).orElseThrow().getInventories().get(0).getContent().stream()
                .filter(item -> item.getId().equals(gun.getId())).findFirst().orElseThrow();
        assertThat(loaded.getProperties()).containsKey("slug");

        // удар 10 и 1, зона выбрана; урон 4D6: 6 + 6 + 6 + 6 + 2 = 26; SP 14 → 7; прошло 19, мягкая ÷2 = 9; BTM 2 → 7
        when(dice.roll(anyInt())).thenReturn(10, 1, 6, 6, 6, 6);
        AttackResult result = attackService.attack(attacker.getId(), gun.getId(),
                new AttackRequest(List.of(victim.getId()), null, 1, "TORSO", null, Set.of(), 0, "SINGLE", 0, 0));
        flush();

        assertThat(result.steps()).anyMatch(step -> step.contains("4d6+2") || step.contains("4D6+2"));
        assertThat(damage()).isEqualTo(7);
    }
}
