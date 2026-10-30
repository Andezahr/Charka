package app.charka.system.cp2020;

import app.charka.attack.AttackForm;
import app.charka.attack.AttackHit;
import app.charka.attack.AttackRequest;
import app.charka.check.CheckRequest;
import app.charka.item.CatalogItem;
import app.charka.model.Character;
import app.charka.model.CharacterSkill;
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

@DisplayName("Атака оружием CP2020")
class Cp2020AttackRulesTest {

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

    private static AttackRequest request(int distance, String location, String... options) {
        return new AttackRequest(null, null, distance, location, null, Set.of(options), 0);
    }

    private static Deque<Integer> rolls(Integer... values) {
        return new ArrayDeque<>(Arrays.asList(values));
    }

    private AttackHit hit(Item weapon, AttackRequest request, Integer... values) {
        Deque<Integer> queue = rolls(values);
        return rules.hits(weapon, request, sides -> queue.removeFirst()).get(0);
    }

    @Nested
    @DisplayName("Сложность по дальности (с. 99)")
    class HitNumber {

        @ParameterizedTest
        @CsvSource({"0,50,10", "1,50,10", "2,50,15", "12,50,15", "13,50,20", "25,50,20", "26,50,25", "50,50,25", "51,50,30", "100,50,30",
                "37,150,15", "38,150,20", "400,400,25"})
        void bands(int distance, int range, int expected) {
            assertThat(Cp2020AttackRules.hitNumber(distance, range)).isEqualTo(expected);
        }

        @Test
        @DisplayName("Дальше двойной дальности выстрелить нельзя")
        void outOfRange() {
            assertThatThrownBy(() -> Cp2020AttackRules.hitNumber(101, 50))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("100 м");
        }
    }

    @Nested
    @DisplayName("Проверка на попадание")
    class ToHit {

        @Test
        @DisplayName("Текущий REF + навык оружия + WA, сложность по дальности")
        void sum() {
            CharacterSkill skill = new CharacterSkill();
            skill.setSkillCode("submachinegun");
            skill.setLevel(4);
            attacker.getSkills().add(skill);
            Item minami = weapon("arasaka_minami_10");

            CheckRequest check = rules.toHit(attacker, minami, request(150, null), STATS);

            assertThat(check.modifier()).isEqualTo(7 + 4 + 0);
            assertThat(check.difficulty()).isEqualTo(25);
            assertThat(check.skillCode()).isEqualTo("submachinegun");
        }

        @Test
        @DisplayName("Выбранная зона даёт −4, модификаторы складываются")
        void modifiers() {
            Item minami = weapon("arasaka_minami_10");

            CheckRequest check = rules.toHit(attacker, minami, request(5, "HEAD", "immobile", "aim_3", "moving_14"), STATS);

            assertThat(check.modifier()).isEqualTo(7 + 4 + 3 - 5 - 4);
            assertThat(check.parts()).extracting(CheckRequest.Part::name).contains("Цель неподвижна", "Прицельный выстрел: Голова");
        }

        @ParameterizedTest
        @CsvSource({"30,0", "75,1", "120,0", "290,2"})
        @DisplayName("Телескопический прицел: +1 на средней дальности, +2 на предельной")
        void telescopic(int distance, int bonus) {
            Item minami = weapon("arasaka_minami_10");
            int plain = rules.toHit(attacker, minami, request(distance, null), STATS).modifier();

            assertThat(rules.toHit(attacker, minami, request(distance, null, Cp2020AttackModifiers.TELESCOPIC), STATS).modifier())
                    .isEqualTo(plain + bonus);
        }

        @Test
        @DisplayName("Незнакомый код и два варианта одного выбора отклоняются")
        void badOptions() {
            Item minami = weapon("arasaka_minami_10");

            assertThatThrownBy(() -> rules.toHit(attacker, minami, request(5, null, "cheat"), STATS))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> rules.toHit(attacker, minami, request(5, null, "aim_1", "aim_3"), STATS))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Навык задаёт тип оружия; у экзотического он на выбор")
        void skills() {
            assertThat(rules.form(weapon("arasaka_rapid_assault_12")).skills()).hasSize(1).first()
                    .satisfies(option -> assertThat(option.value()).isEqualTo("rifle"));
            assertThat(rules.form(weapon("eagletech_tomcat_c_bow")).skills()).hasSizeGreaterThan(1);
            assertThatThrownBy(() -> rules.toHit(attacker, weapon("arasaka_minami_10"),
                    new AttackRequest(null, null, 5, null, "rifle", Set.of(), 0), STATS))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Форма")
    class Form {

        @Test
        @DisplayName("Холодным и одноразовым так не атакуют")
        void noForm() {
            assertThat(rules.form(weapon("knife"))).isNull();
            assertThat(rules.form(weapon("grenade"))).isNull();
        }

        @Test
        @DisplayName("Старое оружие без своей дальности берёт её по типу")
        void defaultRange() {
            assertThat(rules.form(weapon("uzi")).range()).isEqualTo(150);
            assertThat(rules.form(weapon("fn_fal")).range()).isEqualTo(400);
            assertThat(rules.form(weapon("caws")).range()).isEqualTo(50);
        }

        @Test
        @DisplayName("Пустое оружие: атака недоступна с объяснением, сервер тоже отказывает")
        void empty() {
            Item minami = weapon("arasaka_minami_10");
            minami.getProperties().put(Cp2020ItemTypes.LOADED, "0");

            AttackForm form = rules.form(minami);

            assertThat(form.unavailable()).isEqualTo("Магазин пуст");
            assertThatThrownBy(() -> rules.toHit(attacker, minami, request(5, null), STATS))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Магазин пуст");
        }

        @Test
        @DisplayName("Выстрел расходует патрон")
        void spend() {
            Item minami = weapon("arasaka_minami_10");

            rules.spend(minami);

            assertThat(minami.getProperties()).containsEntry(Cp2020ItemTypes.LOADED, "39");
        }

        @Test
        @DisplayName("Все модификаторы таблицы есть в форме")
        void sections() {
            assertThat(Cp2020AttackModifiers.sections()).extracting(AttackForm.Section::name).containsExactly("Цель", "Стрелок", "Оружие");
        }
    }

    @Nested
    @DisplayName("Попадание")
    class Hits {

        @Test
        @DisplayName("Зона 1d10 и урон оружия")
        void randomLocation() {
            AttackHit hit = hit(weapon("arasaka_minami_10"), request(5, null), 3, 4, 5);

            assertThat(hit.damage().getLocation()).isEqualTo("TORSO");
            assertThat(hit.damage().getAmount()).isEqualTo(4 + 5 + 3);
            assertThat(hit.damage().getDamageType()).isEqualTo("NORMAL");
            assertThat(hit.steps()).contains("Бросок зоны: d10 = 3", "Урон 2d6+3: 4 + 5 + 3 = 12");
        }

        @Test
        @DisplayName("Выбранная зона не бросается")
        void chosenLocation() {
            AttackHit hit = hit(weapon("arasaka_minami_10"), request(5, "HEAD"), 1, 1);

            assertThat(hit.damage().getLocation()).isEqualTo("HEAD");
        }

        @Test
        @DisplayName("Заряжены бронебойные — урон AP; Barrett снимает 2 SP")
        void armorPiercing() {
            AttackHit hit = hit(weapon("barrett_arasaka_light_20mm"), request(5, "TORSO"), 1, 1, 1, 1);

            assertThat(hit.damage().getDamageType()).isEqualTo("ARMOR_PIERCING");
            assertThat(hit.damage().getArmorWear()).isEqualTo(2);
        }

        @ParameterizedTest
        @CsvSource({"5,4", "20,3", "50,2", "90,2"})
        @DisplayName("Дробовик теряет кубики с расстоянием (с. 109)")
        void shotgun(int distance, int dice) {
            List<Integer> counted = new ArrayList<>();
            Item shotgun = weapon("arasaka_rapid_assault_12");

            rules.hits(shotgun, request(distance, "TORSO"), sides -> {
                counted.add(sides);
                return 1;
            });

            assertThat(counted).hasSize(dice);
        }

        @Test
        @DisplayName("Урон, который нельзя бросить, остаётся мастеру")
        void manualDamage() {
            Item item = weapon("arasaka_minami_10");
            item.getProperties().put("damage", "Drugs");

            AttackHit hit = hit(item, request(5, null), 3);

            assertThat(hit.damage()).isNull();
            assertThat(hit.steps()).anyMatch(step -> step.contains("Drugs"));
        }

        @Test
        @DisplayName("Урон с делением округляется вниз, но не меньше 1")
        void dividedDamage() {
            Item item = weapon("arasaka_minami_10");
            item.getProperties().put("damage", "1D6/3");

            AttackHit hit = hit(item, request(5, "TORSO"), 5);

            assertThat(hit.damage().getAmount()).isEqualTo(1);
        }
    }
}
