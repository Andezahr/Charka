package app.charka.system.cp2020;

import app.charka.attack.AttackForm;
import app.charka.attack.AttackHit;
import app.charka.attack.AttackRequest;
import app.charka.check.CheckRequest;
import app.charka.check.CheckResult;
import app.charka.item.CatalogItem;
import app.charka.model.Character;
import app.charka.model.Inventory;
import app.charka.model.Item;
import app.charka.skill.StatValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Режимы огня и заклинивание CP2020")
class Cp2020FireModesTest {

    /** REF 7, навыка нет, у Minami WA 0: база проверки 7 */
    private static final Map<String, StatValue> STATS = Map.of("ref", new StatValue("ref", "REF", 8, 7));

    private final Cp2020AttackRules rules = new Cp2020AttackRules();

    private Character attacker;

    @BeforeEach
    void setUp() {
        attacker = new Character();
    }

    private Item weapon(String key) {
        CatalogItem entry = Cp2020Weapons.CATALOG.stream().filter(row -> row.key().equals(key)).findFirst().orElseThrow();
        Inventory inventory = new Inventory();
        inventory.setName("Рюкзак");
        inventory.setContent(new ArrayList<>());
        inventory.setCharacter(attacker);
        attacker.getInventories().add(inventory);
        Item item = new Item();
        item.setName(entry.name());
        item.setQuantity(1);
        item.setType(entry.typeCode());
        item.getProperties().putAll(entry.properties());
        item.setInventory(inventory);
        inventory.getContent().add(item);
        return item;
    }

    private Item minami() {
        return weapon("arasaka_minami_10");
    }

    private static AttackRequest fire(String mode, int distance, int rounds, int width, String... options) {
        return new AttackRequest(List.of(), null, distance, null, null, Set.of(options), 0, mode, rounds, width);
    }

    private int modifier(Item weapon, AttackRequest request) {
        return rules.toHit(attacker, weapon, request, STATS).modifier();
    }

    private List<AttackHit> hits(Item weapon, AttackRequest request, int total, int difficulty, Integer... values) {
        Deque<Integer> queue = new ArrayDeque<>(Arrays.asList(values));
        return rules.hits(weapon, request, new CheckResult("", List.of(), total, difficulty, true, false, false), sides -> queue.removeFirst());
    }

    @Nested
    @DisplayName("Режимы (с. 106)")
    class Modes {

        @Test
        @DisplayName("Режимы есть у оружия с ROF от 3; пистолету и дробовику только одиночный")
        void available() {
            assertThat(rules.form(minami()).modes()).extracting(AttackForm.Mode::code)
                    .containsExactly("SINGLE", "BURST", "AUTO", "SUPPRESS");
            assertThat(rules.form(weapon("budgetarms_c_13")).modes()).extracting(AttackForm.Mode::code).containsExactly("SINGLE");
            assertThat(rules.form(weapon("arasaka_rapid_assault_12")).modes()).extracting(AttackForm.Mode::code).containsExactly("SINGLE", "PATTERNS");
            assertThatThrownBy(() -> rules.toHit(attacker, weapon("budgetarms_c_13"), fire("BURST", 5, 0, 0), STATS))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @CsvSource({"5,10", "50,10", "100,10", "150,7", "300,7"})
        @DisplayName("Очередь: +3 только на близкой и средней дальности")
        void burstBonus(int distance, int expected) {
            assertThat(modifier(minami(), fire("BURST", distance, 0, 0))).isEqualTo(expected);
        }

        @Test
        @DisplayName("Очередь тратит 3 патрона и попадает 1D6/2 раз, каждая пуля со своей зоной")
        void burstHits() {
            Item minami = minami();
            rules.spend(minami, fire("BURST", 5, 0, 0));
            assertThat(minami.getProperties()).containsEntry(Cp2020ItemTypes.LOADED, "37");

            List<AttackHit> hits = hits(minami, fire("BURST", 5, 0, 0), 20, 15, 5, 3, 1, 1, 7, 1, 1);

            assertThat(hits).hasSize(2);
            assertThat(hits.get(0).damage().getLocation()).isEqualTo("TORSO");
            assertThat(hits.get(1).damage().getLocation()).isEqualTo("R_LEG");
        }

        @Test
        @DisplayName("Очередь попадает хотя бы раз: 1 на d6 не даёт ноль (с. 25)")
        void burstAtLeastOne() {
            assertThat(hits(minami(), fire("BURST", 5, 0, 0), 20, 15, 1, 3, 1, 1)).hasSize(1);
        }

        @ParameterizedTest
        @CsvSource({"5,20,9", "5,9,7", "50,20,9", "100,20,5", "150,20,5", "150,5,7"})
        @DisplayName("Автоогонь: +1 за 10 патронов на близкой, −1 за 10 дальше")
        void autoModifier(int distance, int rounds, int expected) {
            assertThat(modifier(minami(), fire("AUTO", distance, rounds, 0))).isEqualTo(expected);
        }

        @Test
        @DisplayName("Автоогонь: попаданий столько, на сколько превышена сложность, не больше выпущенного")
        void autoHits() {
            Integer[] dice = {3, 1, 1, 3, 1, 1, 3, 1, 1};

            assertThat(hits(minami(), fire("AUTO", 5, 10, 0), 18, 15, dice)).hasSize(3);
            assertThat(hits(minami(), fire("AUTO", 5, 2, 0), 40, 15, dice)).hasSize(2);
            assertThat(hits(minami(), fire("AUTO", 5, 10, 0), 15, 15)).isEmpty();
        }

        @Test
        @DisplayName("Автоогонь: не больше ROF и заряда, без прицела")
        void autoLimits() {
            Item minami = minami();

            assertThatThrownBy(() -> rules.toHit(attacker, minami, fire("AUTO", 5, 21, 0), STATS))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ROF");
            assertThatThrownBy(() -> rules.toHit(attacker, minami, fire("AUTO", 5, 10, 0, "aim_1"), STATS))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("прицел");
            minami.getProperties().put(Cp2020ItemTypes.LOADED, "5");
            assertThatThrownBy(() -> rules.toHit(attacker, minami, fire("AUTO", 5, 10, 0), STATS))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("только 5");
        }

        @Test
        @DisplayName("Зону выбрать можно только одиночным")
        void locationOnlySingle() {
            AttackRequest aimed = new AttackRequest(List.of(), null, 5, "HEAD", null, Set.of(), 0, "BURST", 0, 0);

            assertThatThrownBy(() -> rules.toHit(attacker, minami(), aimed, STATS)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Быстрое выхватывание несовместимо с прицелом и прицеливанием (с. 97)")
        void fastDraw() {
            assertThatThrownBy(() -> rules.toHit(attacker, minami(), fire("SINGLE", 5, 0, 0, "fast_draw", "laser"), STATS))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(modifier(minami(), fire("SINGLE", 5, 0, 0, "fast_draw"))).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("Подавляющий огонь (с. 106–107)")
    class Suppress {

        @Test
        @DisplayName("Цель бросает REF + Атлетика против патроны / ширина")
        void save() {
            CheckRequest save = rules.zoneSave(attacker, minami(), fire("SUPPRESS", 20, 20, 2), STATS);

            assertThat(save.difficulty()).isEqualTo(10);
            assertThat(save.modifier()).isEqualTo(7);
        }

        @Test
        @DisplayName("Цель без листа: её итог вводит мастер")
        void saveNoSheet() {
            AttackRequest request = new AttackRequest(List.of(), "Бустер", 20, null, null, Set.of(), 9, "SUPPRESS", 20, 3);

            CheckRequest save = rules.zoneSave(null, minami(), request, Map.of());

            assertThat(save.difficulty()).isEqualTo(6);
            assertThat(save.modifier()).isEqualTo(9);
        }

        @Test
        @DisplayName("Зона уже 2 м не бывает")
        void minWidth() {
            assertThatThrownBy(() -> rules.zoneSave(attacker, minami(), fire("SUPPRESS", 20, 20, 1), STATS))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Провал: 1D6 пуль в случайные зоны")
        void hits() {
            Deque<Integer> queue = new ArrayDeque<>(List.of(2, 3, 1, 1, 5, 1, 1));

            assertThat(rules.zoneHits(minami(), fire("SUPPRESS", 20, 20, 2), sides -> queue.removeFirst())).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Заклинивание (с. 106)")
    class Jam {

        @ParameterizedTest
        @CsvSource({"VR,3,true", "VR,4,false", "ST,5,true", "ST,6,false", "UR,8,true", "UR,9,false"})
        void byReliability(String reliability, int roll, boolean jammed) {
            Item minami = minami();
            minami.getProperties().put("reliability", reliability);

            List<String> steps = rules.onFumble(minami, sides -> roll);

            assertThat(minami.getProperties().containsKey(Cp2020ItemTypes.JAMMED)).isEqualTo(jammed);
            assertThat(steps).hasSize(1);
        }

        @Test
        @DisplayName("Неавтоматическое оружие не клинит: у него таблица фамблов")
        void notAutomatic() {
            Item pistol = weapon("budgetarms_c_13");

            assertThat(rules.onFumble(pistol, sides -> 1)).isEmpty();
            assertThat(rules.toHit(attacker, pistol, fire("SINGLE", 5, 0, 0), STATS).skillCode()).isEqualTo("handgun");
            assertThat(rules.toHit(attacker, minami(), fire("SINGLE", 5, 0, 0), STATS).skillCode()).isNull();
        }

        @Test
        @DisplayName("Заклинившее не стреляет, пока не расклинить")
        void blockedUntilUnjam() {
            Item minami = minami();
            minami.getProperties().put(Cp2020ItemTypes.JAMMED, "1");

            assertThat(rules.form(minami).unavailable()).contains("заклинило");
            assertThatThrownBy(() -> Cp2020WeaponActions.perform(minami, "FIRE", Map.of())).isInstanceOf(IllegalArgumentException.class);

            Cp2020WeaponActions.perform(minami, "UNJAM", Map.of());

            assertThat(rules.form(minami).unavailable()).isNull();
        }
    }
}
