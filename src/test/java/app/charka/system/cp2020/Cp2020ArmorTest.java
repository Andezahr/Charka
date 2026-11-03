package app.charka.system.cp2020;

import app.charka.item.CatalogItem;
import app.charka.item.ItemAction;
import app.charka.item.ItemTypeDefinition;
import app.charka.model.Character;
import app.charka.model.Inventory;
import app.charka.model.Item;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.CounterContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Броня CP2020: предметы и слои")
class Cp2020ArmorTest {

    private final Cp2020ArmorStrategy strategy = new Cp2020ArmorStrategy();

    private Character character;
    private Inventory backpack;
    private Counter armor;

    @BeforeEach
    void setUp() {
        character = new Character();
        backpack = new Inventory();
        backpack.setName("Рюкзак");
        backpack.setContent(new ArrayList<>());
        backpack.setCharacter(character);
        character.getInventories().add(backpack);

        armor = new Counter();
        armor.setType(Cp2020ArmorStrategy.TYPE);
        armor.setCharacter(character);
        strategy.initialize(armor, Map.of());
        character.getCounters().add(armor);
    }

    private Item fromCatalog(String key) {
        CatalogItem entry = Cp2020Armors.CATALOG.stream().filter(row -> row.key().equals(key)).findFirst().orElseThrow();
        Item item = new Item();
        item.setName(entry.name());
        item.setQuantity(1);
        item.setType(entry.typeCode());
        item.getProperties().putAll(entry.properties());
        item.setInventory(backpack);
        backpack.getContent().add(item);
        return item;
    }

    private Item wearing(String key) {
        Item item = fromCatalog(key);
        Cp2020ArmorActions.perform(item, Cp2020ArmorActions.WEAR);
        return item;
    }

    private static ItemAction action(Item item, String code) {
        return Cp2020ArmorActions.actions(item).stream().filter(a -> a.code().equals(code)).findFirst().orElseThrow();
    }

    @Nested
    @DisplayName("Пропорциональная броня (с. 102)")
    class Proportional {

        @ParameterizedTest
        @CsvSource({"0,5", "4,5", "5,4", "8,4", "9,3", "14,3", "15,2", "20,2", "21,1", "26,1", "27,0", "40,0"})
        void bonusTable(int difference, int bonus) {
            assertThat(Cp2020ArmorRules.proportionalBonus(difference)).isEqualTo(bonus);
        }

        @Test
        @DisplayName("Три слоя считаются парами изнутри наружу")
        void pairsInsideOut() {
            // 10 и 14 → 14 + 5 = 19; 19 и 18 → 19 + 5 = 24
            assertThat(Cp2020ArmorRules.combine(List.of(10, 14, 18))).isEqualTo(24);
        }

        @Test
        @DisplayName("Слой с SP 0 не добавляет защиты")
        void zeroLayerSkipped() {
            assertThat(Cp2020ArmorRules.combine(List.of(0, 14, 0))).isEqualTo(14);
            assertThat(Cp2020ArmorRules.combine(List.of())).isZero();
        }

        @ParameterizedTest
        @CsvSource({"0,0", "1,0", "2,1", "3,3"})
        void layerPenalty(int layers, int penalty) {
            assertThat(Cp2020ArmorRules.layerPenalty(layers)).isEqualTo(penalty);
        }
    }

    @Nested
    @DisplayName("Надеть и снять")
    class WearAndTakeOff {

        @Test
        @DisplayName("Надетая куртка закрывает торс и руки, снятая — нет")
        void changesZones() {
            Item jacket = wearing("armor_light_jacket");

            assertThat(strategy.sp(armor, Cp2020Location.TORSO)).isEqualTo(14);
            assertThat(strategy.sp(armor, Cp2020Location.L_ARM)).isEqualTo(14);
            assertThat(strategy.sp(armor, Cp2020Location.HEAD)).isZero();
            assertThat(Cp2020ArmorActions.notes(jacket)).containsExactly("Надета");

            Cp2020ArmorActions.perform(jacket, Cp2020ArmorActions.TAKE_OFF);

            assertThat(strategy.sp(armor, Cp2020Location.TORSO)).isZero();
            assertThat(action(jacket, Cp2020ArmorActions.WEAR).unavailable()).isNull();
        }

        @Test
        @DisplayName("Слои складываются по таблице, свой слой счётчика — внутренний")
        void layersCombine() {
            strategy.updateAttributes(armor, Map.of("sp_TORSO", 10));
            wearing("armor_kevlar_vest");
            assertThat(strategy.sp(armor, Cp2020Location.TORSO)).isEqualTo(15);

            wearing("armor_light_jacket");
            assertThat(strategy.maxSp(armor, Cp2020Location.TORSO)).isEqualTo(20);
            assertThat(strategy.sp(armor, Cp2020Location.R_ARM)).isEqualTo(14);
        }

        @Test
        @DisplayName("Четвёртый слой на зону не надеть, ни кнопкой, ни запросом")
        void maxLayers() {
            wearing("armor_kevlar_vest");
            wearing("armor_light_jacket");
            wearing("armor_medium_jacket");
            Item fourth = fromCatalog("armor_heavy_jacket");

            assertThat(action(fourth, Cp2020ArmorActions.WEAR).unavailable()).contains("Торс");
            assertThatThrownBy(() -> Cp2020ArmorActions.perform(fourth, Cp2020ArmorActions.WEAR))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Свой слой счётчика занимает место слоя")
        void ownLayerCounts() {
            strategy.updateAttributes(armor, Map.of("sp_TORSO", 5));
            wearing("armor_kevlar_vest");
            wearing("armor_light_jacket");

            assertThat(action(fromCatalog("armor_medium_jacket"), Cp2020ArmorActions.WEAR).unavailable()).contains("Торс");
        }

        @Test
        @DisplayName("Вторая жёсткая броня на ту же зону запрещена, на другую можно")
        void oneHardLayer() {
            wearing("armor_flak_vest");

            assertThat(action(fromCatalog("armor_door_gunner_vest"), Cp2020ArmorActions.WEAR).unavailable()).contains("жёсткая");
            assertThat(action(fromCatalog("armor_flak_pants"), Cp2020ArmorActions.WEAR).unavailable()).isNull();
            assertThat(action(fromCatalog("armor_kevlar_vest"), Cp2020ArmorActions.WEAR).unavailable()).isNull();
        }

        @Test
        @DisplayName("Шлем и куртка — не слои друг для друга")
        void differentZonesAreNotLayers() {
            wearing("armor_medium_jacket");
            wearing("armor_nylon_helmet");

            assertThat(strategy.sp(armor, Cp2020Location.TORSO)).isEqualTo(18);
            assertThat(strategy.sp(armor, Cp2020Location.HEAD)).isEqualTo(20);
            assertThat(Cp2020ArmorStrategy.encumbrance(armor)).isEqualTo(1);
        }

        @Test
        @DisplayName("Одежда с SP 0 не считается слоем")
        void clothIsNotLayer() {
            wearing("armor_cloth");
            wearing("armor_kevlar_vest");

            assertThat(strategy.sp(armor, Cp2020Location.TORSO)).isEqualTo(10);
            assertThat(Cp2020ArmorStrategy.encumbrance(armor)).isZero();
        }

        @Test
        @DisplayName("Без счётчика брони надеть нельзя: кнопка объясняет почему")
        void needsCounter() {
            character.getCounters().clear();

            assertThat(action(fromCatalog("armor_kevlar_vest"), Cp2020ArmorActions.WEAR).unavailable()).contains("счётчика брони");
        }
    }

    @Nested
    @DisplayName("Износ")
    class Wear {

        @Test
        @DisplayName("Пробитие снимает 1 SP с зоны и пишется на внешний слой")
        void outerLayerWears() {
            Item vest = wearing("armor_kevlar_vest");
            Item jacket = wearing("armor_light_jacket");
            assertThat(strategy.sp(armor, Cp2020Location.TORSO)).isEqualTo(19);

            assertThat(strategy.wear(armor, Cp2020Location.TORSO)).isEqualTo(18);

            assertThat(Cp2020WornArmor.wear(jacket, Cp2020Location.TORSO)).isEqualTo(1);
            assertThat(Cp2020WornArmor.isDamaged(vest)).isFalse();
            assertThat(Cp2020ArmorActions.notes(jacket)).contains("Износ: Торс −1");
        }

        @Test
        @DisplayName("Износ уходит вместе с предметом и возвращается с ним")
        void wearStaysOnItem() {
            Item jacket = wearing("armor_light_jacket");
            strategy.wear(armor, Cp2020Location.TORSO);
            strategy.wear(armor, Cp2020Location.TORSO);

            Cp2020ArmorActions.perform(jacket, Cp2020ArmorActions.TAKE_OFF);
            assertThat(strategy.sp(armor, Cp2020Location.TORSO)).isZero();

            Cp2020ArmorActions.perform(jacket, Cp2020ArmorActions.WEAR);
            assertThat(strategy.sp(armor, Cp2020Location.TORSO)).isEqualTo(12);
        }

        @Test
        @DisplayName("Ремонт счётчика чинит надетое и свой слой, ремонт предмета — только его")
        void repair() {
            strategy.updateAttributes(armor, Map.of("sp_HEAD", 8));
            strategy.wear(armor, Cp2020Location.HEAD);
            Item jacket = wearing("armor_light_jacket");
            strategy.wear(armor, Cp2020Location.TORSO);

            strategy.repair(armor);

            assertThat(strategy.sp(armor, Cp2020Location.HEAD)).isEqualTo(8);
            assertThat(strategy.sp(armor, Cp2020Location.TORSO)).isEqualTo(14);

            strategy.wear(armor, Cp2020Location.TORSO);
            Cp2020ArmorActions.perform(jacket, Cp2020ArmorActions.REPAIR);
            assertThat(strategy.sp(armor, Cp2020Location.TORSO)).isEqualTo(14);
            assertThat(Cp2020ArmorActions.actions(jacket)).extracting(ItemAction::code).containsExactly(Cp2020ArmorActions.TAKE_OFF);
        }
    }

    @Nested
    @DisplayName("Штраф к REF")
    class Encumbrance {

        private int ref() {
            Counter stats = new Counter();
            stats.setType(Cp2020StatsStrategy.TYPE);
            stats.setCharacter(character);
            Cp2020StatsStrategy statsStrategy = new Cp2020StatsStrategy();
            statsStrategy.initialize(stats, Map.of(Cp2020StatsStrategy.REF, 8));
            return statsStrategy.describe(new CounterContext(stats, List.of(stats, armor))).getStats().stream()
                    .filter(line -> line.getKey().equals(Cp2020StatsStrategy.REF))
                    .findFirst().orElseThrow()
                    .getEffective();
        }

        @Test
        @DisplayName("EV надетого вычитается из REF, снятое не считается")
        void evFromWorn() {
            assertThat(ref()).isEqualTo(8);

            Item vest = wearing("armor_door_gunner_vest");
            assertThat(ref()).isEqualTo(5);

            Cp2020ArmorActions.perform(vest, Cp2020ArmorActions.TAKE_OFF);
            assertThat(ref()).isEqualTo(8);
        }

        @Test
        @DisplayName("Второй слой −1, третий ещё −2")
        void layerPenalties() {
            wearing("armor_kevlar_vest");
            assertThat(Cp2020ArmorStrategy.encumbrance(armor)).isZero();

            wearing("armor_light_jacket");
            assertThat(Cp2020ArmorStrategy.encumbrance(armor)).isEqualTo(1);

            wearing("armor_medium_jacket");
            assertThat(Cp2020ArmorStrategy.encumbrance(armor)).isEqualTo(1 + 3);
            assertThat(ref()).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("Тип и справочник")
    class Type {

        private final ItemTypeDefinition type = Cp2020ItemTypes.ARMOR_TYPE;

        @Test
        @DisplayName("Все строки справочника проходят проверку типа")
        void catalogValid() {
            Cp2020Armors.CATALOG.forEach(row -> assertThat(type.normalize(row.properties())).isEqualTo(row.properties()));
            assertThat(Cp2020Armors.CATALOG).extracting(CatalogItem::key).doesNotHaveDuplicates();
        }

        @ParameterizedTest
        @CsvSource({
                "armor_heavy_leather, 4, 0, 50, SOFT",
                "armor_kevlar_vest, 10, 0, 90, SOFT",
                "armor_steel_helmet, 14, 0, 20, HARD",
                "armor_medium_jacket, 18, 1, 200, SOFT",
                "armor_flak_pants, 20, 1, 200, HARD",
                "armor_heavy_jacket, 20, 2, 250, SOFT",
                "armor_door_gunner_vest, 25, 3, 250, HARD",
                "armor_metalgear, 25, 2, 600, HARD"
        })
        void bookRows(String key, String sp, String ev, int cost, String hardness) {
            CatalogItem row = Cp2020Armors.CATALOG.stream().filter(e -> e.key().equals(key)).findFirst().orElseThrow();

            assertThat(row.properties()).containsEntry(Cp2020ItemTypes.ARMOR_SP, sp)
                    .containsEntry(Cp2020ItemTypes.ENCUMBRANCE, ev)
                    .containsEntry(Cp2020ItemTypes.HARDNESS, hardness);
            assertThat(row.cost()).isEqualTo(cost);
        }

        @Test
        @DisplayName("SP и хотя бы одна зона обязательны")
        void required() {
            assertThatThrownBy(() -> type.normalize(Map.of("hardness", "SOFT", "zone_TORSO", "1")))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SP");
            assertThatThrownBy(() -> type.normalize(Map.of("hardness", "SOFT", "sp", "10")))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("зону");
        }

        @Test
        @DisplayName("Форма не может задать состояние напрямую")
        void stateNotFromForm() {
            Map<String, String> values = type.normalize(Map.of("hardness", "SOFT", "sp", "10", "zone_TORSO", "1", "worn", "1", "wear_TORSO", "5"));

            assertThat(values).doesNotContainKeys("worn", "wear_TORSO");
            assertThat(type.stateKeys()).contains("worn", "wear_TORSO");
        }
    }
}
