package app.charka.system.cp2020;

import app.charka.attack.AttackRequest;
import app.charka.attack.AttackResult;
import app.charka.attack.AttackService;
import app.charka.encounter.EncounterService;
import app.charka.encounter.EncounterView;
import app.charka.item.CatalogItem;
import app.charka.item.InstalledBlock;
import app.charka.item.ItemAction;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.service.CharacterService;
import app.charka.service.ItemService;
import app.charka.system.GameSystemRegistry;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BODY 6 и EMP 6 у всех по умолчанию: человечность 60, BTM 2 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
@DisplayName("Киберware CP2020")
class Cp2020CyberTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private CharacterService characterService;
    @Autowired private ItemService itemService;
    @Autowired private EncounterService encounterService;
    @Autowired private AttackService attackService;
    @Autowired private GameSystemRegistry registry;
    @Autowired private EntityManager entityManager;

    @MockitoBean(answers = Answers.CALLS_REAL_METHODS) private DiceRoller dice;

    private Campaign campaign;
    private Character owner;
    private Character other;

    @BeforeEach
    void setUp() {
        Campaign created = new Campaign();
        created.setName("Тест");
        created.setSystem("CP2020");
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        campaign = campaignRepository.save(created);
        owner = member("Хром");
        other = member("Другой");
        flush();
        when(dice.roll(anyInt())).thenReturn(3);
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

    private Character owner() {
        return characterRepository.findById(owner.getId()).orElseThrow();
    }

    private Counter counter(String type) {
        return owner().getCounters().stream().filter(counter -> type.equals(counter.getType())).findFirst().orElseThrow();
    }

    private int humanityLoss() {
        return counter(Cp2020StatsStrategy.TYPE).getAttribute(Cp2020StatsStrategy.HUMANITY_LOSS, 0);
    }

    private Item buy(String name) {
        CatalogItem row = Cp2020Cyberware.CATALOG.stream().filter(entry -> entry.name().equals(name)).findFirst().orElseThrow();
        Item item = itemService.addFromCatalog(owner().getInventories().get(0).getId(), row.key(), 1);
        flush();
        return item;
    }

    private String install(String name) {
        String result = itemService.performAction(owner.getId(), buy(name).getId(), "INSTALL", Map.of());
        flush();
        return result;
    }

    private InstalledBlock block() {
        return registry.forCharacter(owner()).getInstalled(owner());
    }

    @Nested
    @DisplayName("Справочник")
    class Catalog {

        @Test
        @DisplayName("Коды уникальны, каждая строка проходит проверку полей без потерь")
        void valid() {
            assertThat(Cp2020Cyberware.CATALOG).hasSizeGreaterThan(120);
            assertThat(Cp2020Cyberware.CATALOG).extracting(CatalogItem::key).doesNotHaveDuplicates();
            assertThat(Cp2020Cyberware.CATALOG).allSatisfy(entry ->
                    assertThat(Cp2020ItemTypes.CYBER_TYPE.normalize(entry.properties())).isEqualTo(entry.properties()));
        }

        @Test
        @DisplayName("Названия базовых модулей существуют: иначе опцию никогда не установить")
        void parentsExist() {
            Set<String> names = Set.copyOf(Cp2020Cyberware.CATALOG.stream().map(CatalogItem::name).toList());
            Cp2020Cyberware.CATALOG.stream()
                    .map(entry -> entry.properties().get(Cp2020ItemTypes.REQUIRES))
                    .filter(java.util.Objects::nonNull)
                    .flatMap(requires -> List.of(requires.split("\\|")).stream())
                    .forEach(parent -> assertThat(names).contains(parent));
        }
    }

    @Nested
    @DisplayName("Установка")
    class Install {

        @Test
        @DisplayName("Потеря бросается и копится, хирургия ранит: Minor 1D6+1, без BTM")
        void humanityAndSurgery() {
            // 3D6 = 9, хирургия 1D6+1 = 4
            String result = install("Rippers");

            assertThat(humanityLoss()).isEqualTo(9);
            assertThat(result).contains("Человечность −9").contains("Minor").contains("DC 15").contains("4 урона");
            assertThat(counter(Cp2020HealthStrategy.TYPE).getValue()).isEqualTo(4);
        }

        @Test
        @DisplayName("Дробная потеря вверх; Negligible — 1 урон")
        void fractionAndNegligible() {
            install("Light Tattoo");

            assertThat(humanityLoss()).isEqualTo(1);
            assertThat(counter(Cp2020HealthStrategy.TYPE).getValue()).isEqualTo(1);
        }

        @Test
        @DisplayName("Опция без базового модуля не ставится: причина на кнопке и на сервере")
        void requiresParent() {
            Item scope = buy("Targeting Scope");
            Item loaded = owner().getInventories().get(0).getContent().stream().filter(item -> item.getId().equals(scope.getId())).findFirst().orElseThrow();

            assertThat(Cp2020Cyber.actions(loaded).get(0).unavailable()).contains("Cyberoptic");
            assertThatThrownBy(() -> itemService.performAction(owner.getId(), scope.getId(), "INSTALL", Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);

            install("Cyberoptic");
            assertThat(itemService.performAction(owner.getId(), scope.getId(), "INSTALL", Map.of())).contains("установлен");
        }

        @Test
        @DisplayName("Подтверждение перед необратимым: на кнопке есть вопрос")
        void confirm() {
            Item item = buy("Skinwatch");
            Item loaded = owner().getInventories().get(0).getContent().stream().filter(row -> row.getId().equals(item.getId())).findFirst().orElseThrow();

            assertThat(Cp2020Cyber.actions(loaded)).extracting(ItemAction::confirm).allMatch(text -> text != null && text.contains("Установить"));
        }

        @Test
        @DisplayName("Снять: человечность не возвращается; поставить обратно: нового броска нет, хирургия есть")
        void removeAndBack() {
            Item item = buy("Big Knucks");
            itemService.performAction(owner.getId(), item.getId(), "INSTALL", Map.of());
            flush();
            int lost = humanityLoss();
            assertThat(lost).isEqualTo(9);

            String removed = itemService.performAction(owner.getId(), item.getId(), "REMOVE", Map.of());
            flush();
            assertThat(removed).contains("не возвращается");
            assertThat(humanityLoss()).isEqualTo(lost);
            assertThat(block().rows()).isEmpty();

            itemService.performAction(owner.getId(), item.getId(), "INSTALL", Map.of());
            flush();
            assertThat(humanityLoss()).isEqualTo(lost);
            assertThat(block().rows()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Что даёт установленное")
    class Effects {

        @Test
        @DisplayName("Muscle Lace: BODY +2 в статах, BTM и спасбросках")
        void body() {
            install("Muscle and Bone Lace");

            assertThat(registry.forCharacter(owner()).getCurrentStats(owner()).get("body").value()).isEqualTo(8);
            assertThat(Cp2020StatsStrategy.body(counter(Cp2020StatsStrategy.TYPE))).isEqualTo(8);
        }

        @Test
        @DisplayName("Subdermal: слой брони торса SP 18; Skin Weave: слой, но без штрафа к REF; Torso Plate −3 REF")
        void armor() {
            install("Subdermal Armor");
            assertThat(new Cp2020ArmorStrategy().sp(counter(Cp2020ArmorStrategy.TYPE), Cp2020Location.TORSO)).isEqualTo(18);
            assertThat(Cp2020ArmorStrategy.encumbrance(counter(Cp2020ArmorStrategy.TYPE))).isZero();

            install("Skin Weave");
            assertThat(new Cp2020ArmorStrategy().sp(counter(Cp2020ArmorStrategy.TYPE), Cp2020Location.HEAD)).isEqualTo(12);
            assertThat(Cp2020ArmorStrategy.encumbrance(counter(Cp2020ArmorStrategy.TYPE))).isZero();
        }

        @Test
        @DisplayName("Жёсткого слоя второго на зоне нет: как у надетой брони")
        void hardLayer() {
            install("Subdermal Armor");
            Item plate = buy("Torso Plate");

            assertThatThrownBy(() -> itemService.performAction(owner.getId(), plate.getId(), "INSTALL", Map.of()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("жёсткая");
        }

        @Test
        @DisplayName("Kerenzikov прибавляется к инициативе; пока не установлен — нет")
        void kerenzikov() {
            install(Cp2020Cyberware.PROCESSOR);
            install("Kerenzikov +2");

            encounterService.start(campaign.getId(), List.of(owner.getId(), other.getId()));
            flush();

            EncounterView.Row row = row(owner);
            // REF 6 с серьёзной раной от операций −2, d10 = 3, ускоритель 2
            assertThat(row.participant().getInitiativeDetails()).contains("ускоритель 2").endsWith("= 9");
            assertThat(row.participant().getInitiative()).isEqualTo(9);
        }

        @Test
        @DisplayName("Speedware: включить, раунд ожидания, затем +3 на 5 раундов")
        void speedware() {
            install(Cp2020Cyberware.PROCESSOR);
            install(Cp2020Cyber.SPEEDWARE);
            encounterService.start(campaign.getId(), List.of(owner.getId(), other.getId()));
            flush();
            int before = row(owner).participant().getInitiative();
            int gap = before - row(other).participant().getInitiative();

            assertThat(row(owner).declarations()).extracting(declaration -> declaration.code()).contains("SPEEDWARE");
            assertThat(row(other).declarations()).extracting(declaration -> declaration.code()).doesNotContain("SPEEDWARE");
            encounterService.declare(campaign.getId(), row(owner).participant().getId(), "SPEEDWARE");
            flush();
            assertThat(row(owner).participant().getInitiative()).isEqualTo(before);
            assertThat(row(owner).declarations()).extracting(declaration -> declaration.code()).doesNotContain("SPEEDWARE");

            for (int round = 0; round < 5; round++) {
                nextRound();
                assertThat(row(owner).participant().getInitiative() - row(other).participant().getInitiative()).isEqualTo(gap + 3);
            }
            nextRound();
            assertThat(row(owner).participant().getInitiative() - row(other).participant().getInitiative()).isEqualTo(gap);
            assertThat(row(owner).effects()).isEmpty();
        }

        @Test
        @DisplayName("Rippers бьют в трекере как оружие, против брони как нож; пока не установлены — нет")
        void bodyWeapon() {
            Item rippers = buy("Rippers");
            encounterService.start(campaign.getId(), List.of(owner.getId(), other.getId()));
            flush();
            assertThat(row(owner).weapons()).isEmpty();

            itemService.performAction(owner.getId(), rippers.getId(), "INSTALL", Map.of());
            flush();
            assertThat(row(owner).weapons()).extracting(weapon -> weapon.item().getName()).containsExactly("Rippers");
            assertThat(row(owner).weapons().get(0).actions()).noneMatch(ItemAction::isShownInCombat);

            // защита 1; удар 9; зона выбрана; урон 3 + 3 = 6, BTM −2
            when(dice.roll(anyInt())).thenReturn(1, 9, 3);
            AttackResult result = attackService.attack(owner.getId(), rippers.getId(),
                    new AttackRequest(List.of(other.getId()), null, 0, "TORSO", null, Set.of(), 0, "MELEE", 0, 0));

            assertThat(result.hit()).isTrue();
            assertThat(result.steps()).anyMatch(step -> step.contains("d6+3"));
        }

        @Test
        @DisplayName("Linear Frame: сила 14 вместо BODY даёт +6 к удару")
        void frame() {
            install("Linear Frame β");
            encounterService.start(campaign.getId(), List.of(owner.getId(), other.getId()));
            flush();

            when(dice.roll(anyInt())).thenReturn(1, 9, 2);
            AttackResult result = attackService.attackWithAbility(owner.getId(), "BRAWL",
                    new AttackRequest(List.of(other.getId()), null, 0, "TORSO", null, Set.of(), 0, "KICK", 0, 0));

            assertThat(result.steps()).anyMatch(step -> step.contains("Сила 14: +6"));
        }

        private EncounterView.Row row(Character character) {
            return encounterService.view(campaign.getId()).orElseThrow().rows().stream()
                    .filter(row -> row.character().getId().equals(character.getId())).findFirst().orElseThrow();
        }

        private void nextRound() {
            int round = encounterService.view(campaign.getId()).orElseThrow().encounter().getRound();
            while (encounterService.view(campaign.getId()).orElseThrow().encounter().getRound() == round) {
                encounterService.next(campaign.getId());
                flush();
            }
        }
    }

    @Nested
    @DisplayName("Киберпсихоз и терапия (с. 73–75)")
    class Psychosis {

        private void lost(int points) {
            counter(Cp2020StatsStrategy.TYPE).getAttributes().put(Cp2020StatsStrategy.HUMANITY_LOSS, points);
            flush();
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.CsvSource({"20,", "35,EMP 3", "45,EMP 2", "55,EMP 1", "60,Киберпсихоз", "90,Киберпсихоз"})
        @DisplayName("Предупреждение по текущему EMP: 3, 2, 1, а на 0 и ниже персонажа ведёт мастер")
        void levels(int points, String expected) {
            lost(points);

            if (expected == null) {
                assertThat(block().warning()).isNull();
            } else {
                assertThat(block().warning()).startsWith(expected);
            }
        }

        @Test
        @DisplayName("Трекер и кампания видят только опасное")
        void shortWarning() {
            lost(55);
            assertThat(Cp2020Cyber.shortWarning(owner())).contains("EMP 1");
            lost(60);
            assertThat(registry.forCharacter(owner()).getCombatCard(owner()).lines()).contains("Киберпсих");
            assertThat(registry.forCharacter(owner()).getCharacterBrief(owner())).contains("Киберпсих");
        }

        @Test
        @DisplayName("Установка, опускающая EMP, говорит об этом сразу")
        void installWarns() {
            lost(50);

            assertThat(install("Rippers")).contains("EMP 1");
        }

        @Test
        @DisplayName("Полная терапия: EMP −3 до 6 за 5 недель, как в примере книги")
        void fullTherapy() {
            lost(90);
            assertThat(block().actionsNote()).contains("5 нед");

            String result = itemService.performInstalledAction(owner.getId(), "THERAPY", Map.of("source", "FULL", "amount", "5"));
            flush();

            assertThat(result).contains("90 → 0");
            assertThat(humanityLoss()).isZero();
            assertThat(block().actions()).isEmpty();
        }

        @Test
        @DisplayName("Под наблюдением: 2 очка в неделю, кибер остаётся")
        void monitored() {
            install("Rippers");
            int before = humanityLoss();

            itemService.performInstalledAction(owner.getId(), "THERAPY", Map.of("source", "MONITORED", "amount", "2"));
            flush();

            assertThat(humanityLoss()).isEqualTo(before - 4);
            assertThat(block().rows()).hasSize(1);
        }

        @Test
        @DisplayName("Плохой ввод отклоняется")
        void refused() {
            lost(10);
            assertThatThrownBy(() -> itemService.performInstalledAction(owner.getId(), "THERAPY", Map.of("source", "FULL", "amount", "0")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> itemService.performInstalledAction(owner.getId(), "THERAPY", Map.of("source", "X", "amount", "1")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> itemService.performInstalledAction(owner.getId(), "NOPE", Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("На листе: фишка предупреждения и терапия; итог в блоке")
        void onSheet() throws Exception {
            lost(45);
            String html = mockMvc.perform(get("/character/" + owner.getId())).andReturn().getResponse().getContentAsString();
            assertThat(html).contains("EMP 2: холоден").contains("/installed/THERAPY");

            mockMvc.perform(post("/character/" + owner.getId() + "/installed/THERAPY").param("source", "FULL").param("amount", "1"))
                    .andExpect(status().is3xxRedirection());
            flush();
            assertThat(humanityLoss()).isEqualTo(25);
        }
    }

    @Nested
    @DisplayName("Лист")
    class Sheet {

        private String sheet() throws Exception {
            return mockMvc.perform(get("/character/" + owner.getId())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        }

        @Test
        @DisplayName("Установленное — фишкой в блоке, а не строкой инвентаря; неустановленное — строкой с кнопкой")
        void compact() throws Exception {
            install("Rippers");
            buy("Gills");
            flush();

            String html = sheet();

            assertThat(html).contains("Человечность 51/60").contains("Rippers · 1D6+3");
            assertThat(html).doesNotContain("class=\"row item-row\" data-editable id=\"item-");
            assertThat(html).contains("/action/INSTALL").contains("/action/REMOVE");
            assertThat(html.split("item-row").length).isLessThan(3);
        }

        @Test
        @DisplayName("Из блока «＋» выбор из справочника сразу устанавливает, итог на листе")
        void addThenInstall() throws Exception {
            Long inventory = owner().getInventories().get(0).getId();
            String key = Cp2020Cyberware.keyOf("Skinwatch");

            mockMvc.perform(post("/character/" + owner.getId() + "/inventories/" + inventory + "/items/catalog")
                            .param("catalogKey", key).param("thenAction", "INSTALL"))
                    .andExpect(status().is3xxRedirection());
            flush();

            assertThat(block().rows()).extracting(row -> row.item().getName()).containsExactly("Skinwatch");
        }

        @Test
        @DisplayName("В кампании без киберware блока нет")
        void otherSystems() {
            assertThat(registry.getGeneric().getInstalled(owner())).isNull();
        }
    }
}
