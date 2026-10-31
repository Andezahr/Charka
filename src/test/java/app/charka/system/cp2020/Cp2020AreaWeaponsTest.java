package app.charka.system.cp2020;

import app.charka.attack.AreaDamage;
import app.charka.attack.AttackForm;
import app.charka.attack.AttackHit;
import app.charka.attack.AttackRequest;
import app.charka.check.CheckRequest;
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

@DisplayName("Метание и урон по площади CP2020")
class Cp2020AreaWeaponsTest {

    /** BODY 6: граната летит на 60 м, прочее метательное на 18 м */
    private static final Map<String, StatValue> STATS = Map.of(
            "ref", new StatValue("ref", "REF", 8, 7),
            "body", new StatValue("body", "BODY", 6, 6));

    private final Cp2020AttackRules rules = new Cp2020AttackRules();

    private Character thrower;

    @BeforeEach
    void setUp() {
        thrower = new Character();
    }

    private Item weapon(String key, int quantity) {
        CatalogItem entry = Cp2020Weapons.CATALOG.stream().filter(row -> row.key().equals(key)).findFirst().orElseThrow();
        Inventory inventory = new Inventory();
        inventory.setName("Рюкзак");
        inventory.setContent(new ArrayList<>());
        inventory.setCharacter(thrower);
        thrower.getInventories().add(inventory);
        Item item = new Item();
        item.setName(entry.name());
        item.setQuantity(quantity);
        item.setType(entry.typeCode());
        item.getProperties().putAll(entry.properties());
        item.setInventory(inventory);
        inventory.getContent().add(item);
        return item;
    }

    private static AttackRequest attack(String mode, int distance, int rounds) {
        return new AttackRequest(List.of(), null, distance, null, null, Set.of(), 0, mode, rounds, 0);
    }

    private static Deque<Integer> rolls(Integer... values) {
        return new ArrayDeque<>(Arrays.asList(values));
    }

    @Nested
    @DisplayName("Форма")
    class Form {

        @Test
        @DisplayName("Гранату метают и подрывают, мину только подрывают, сюрикен метают")
        void modes() {
            assertThat(rules.form(weapon("grenade_fragmentation", 1), STATS).modes()).extracting(AttackForm.Mode::code)
                    .containsExactly("THROW", "PLANT");
            assertThat(rules.form(weapon("mine_all_types", 1), STATS).modes()).extracting(AttackForm.Mode::code)
                    .containsExactly("PLANT");
            assertThat(rules.form(weapon("shiriken", 1), STATS).modes()).extracting(AttackForm.Mode::code)
                    .containsExactly("SINGLE");
        }

        @Test
        @DisplayName("Дальность зависит от BODY (с. 99, 108): граната ×10, прочее ×3")
        void range() {
            assertThat(rules.form(weapon("grenade_fragmentation", 1), STATS).range()).isEqualTo(60);
            assertThat(rules.form(weapon("knife", 1), STATS).range()).isEqualTo(18);
        }

        @Test
        @DisplayName("Без BODY метать нельзя, а мину подорвать можно")
        void noBody() {
            assertThat(rules.form(weapon("shiriken", 1), Map.of()).unavailable()).contains("BODY");
            assertThat(rules.form(weapon("mine_all_types", 1), Map.of()).unavailable()).isNull();
        }

        @Test
        @DisplayName("Кнопка говорит, что случится: сюрикен метают, ножом сначала бьют")
        void actionLabel() {
            assertThat(rules.form(weapon("knife", 1), STATS).actionLabel()).isEqualTo("Атаковать");
            assertThat(rules.form(weapon("knife", 1), STATS).modes()).extracting(AttackForm.Mode::code).containsExactly("MELEE", "SINGLE");
            assertThat(rules.form(weapon("shiriken", 1), STATS).actionLabel()).isEqualTo("Метнуть");
            assertThat(rules.form(weapon("mine_all_types", 1), STATS).actionLabel()).isEqualTo("Подорвать");
            assertThat(rules.form(weapon("arasaka_rapid_assault_12", 1), STATS).actionLabel()).isEqualTo("Атаковать");
        }

        @Test
        @DisplayName("Несколько пятен предлагают 2, а не все 10: каждое лишнее даёт −2")
        void patternsDefault() {
            assertThat(rules.form(weapon("arasaka_rapid_assault_12", 1), STATS).defaultRounds()).isEqualTo(2);
        }

        @Test
        @DisplayName("Расстояние полосами таблицы, чтобы не набирать число")
        void bands() {
            assertThat(rules.form(weapon("arasaka_rapid_assault_12", 1), STATS).bands())
                    .extracting(AttackForm.Band::meters).containsExactly(1, 12, 25, 50, 100);
        }

        @Test
        @DisplayName("Метателю не предлагают турель и смартган; частое открыто, остальное свёрнуто")
        void modifiersApply() {
            List<String> codes = rules.form(weapon("shiriken", 1), STATS).sections().stream()
                    .flatMap(section -> section.choices().stream())
                    .flatMap(choice -> choice.options().stream())
                    .map(option -> option.value())
                    .toList();

            assertThat(codes).contains("immobile", "aim_1", "ambush").doesNotContain("turret", "smartgun", "hip", "scope_telescopic");
            assertThat(rules.form(weapon("shiriken", 1), STATS).sections())
                    .filteredOn(section -> !section.rare()).extracting(AttackForm.Section::name).containsExactly("Часто");
        }

        @Test
        @DisplayName("Неподходящий модификатор сервер отклоняет")
        void modifierRefused() {
            AttackRequest smart = new AttackRequest(List.of(), null, 5, null, null, Set.of("smartgun"), 0, "SINGLE", 0, 0);

            assertThatThrownBy(() -> rules.toHit(thrower, weapon("shiriken", 1), smart, STATS))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Взрывчатка спрашивает единицы и предлагает одну")
        void units() {
            AttackForm form = rules.form(weapon("c_6_plastic_explosive", 4), STATS);

            assertThat(form.maxRounds()).isEqualTo(4);
            assertThat(form.defaultRounds()).isEqualTo(1);
            assertThat(form.roundsLabel()).contains("кг");
            assertThat(form.modes()).allMatch(AttackForm.Mode::rounds);
        }
    }

    @Nested
    @DisplayName("Бросок")
    class ToHit {

        @Test
        @DisplayName("Навык — Athletics, сложность по дальности броска")
        void athletics() {
            CheckRequest check = rules.toHit(thrower, weapon("grenade_fragmentation", 1), attack("THROW", 20, 0), STATS);

            assertThat(check.difficulty()).isEqualTo(20);
            assertThat(check.parts()).extracting(CheckRequest.Part::name).contains("REF", "Атлетика");
        }

        @ParameterizedTest
        @CsvSource({"1, 15", "2, 20", "5, 25"})
        @DisplayName("Каждый кг сверх первого снимает 10 м (с. 99): 15 м дальше и дальше по таблице")
        void heavyCharge(int kg, int difficulty) {
            CheckRequest check = rules.toHit(thrower, weapon("c_6_plastic_explosive", 5), attack("THROW", 15, kg), STATS);

            assertThat(check.difficulty()).isEqualTo(difficulty);
        }

        @Test
        @DisplayName("Слишком тяжёлый заряд не докинуть")
        void tooHeavy() {
            assertThatThrownBy(() -> rules.toHit(thrower, weapon("c_6_plastic_explosive", 7), attack("THROW", 5, 7), STATS))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("не докинуть");
        }

        @Test
        @DisplayName("Больше единиц, чем есть, израсходовать нельзя")
        void notEnough() {
            assertThatThrownBy(() -> rules.toHit(thrower, weapon("c_6_plastic_explosive", 2), attack("THROW", 5, 3), STATS))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Зону по площади выбрать нельзя")
        void noChosenLocation() {
            AttackRequest aimed = new AttackRequest(List.of(), null, 5, "HEAD", null, Set.of(), 0, "SINGLE", 0, 0);

            assertThatThrownBy(() -> rules.toHit(thrower, weapon("arasaka_rapid_assault_12", 1), aimed, STATS))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Расход")
    class Spend {

        @Test
        @DisplayName("Граната уходит количеством, последняя исчезает из инвентаря")
        void lastOneGone() {
            Item grenade = weapon("grenade_fragmentation", 2);

            rules.spend(grenade, attack("THROW", 5, 0));
            assertThat(grenade.getQuantity()).isEqualTo(1);
            assertThat(grenade.getInventory().getContent()).hasSize(1);

            rules.spend(grenade, attack("THROW", 5, 0));
            assertThat(grenade.getInventory().getContent()).isEmpty();
        }

        @Test
        @DisplayName("Взрывчатка уходит выбранным числом единиц")
        void explosiveUnits() {
            Item c6 = weapon("c_6_plastic_explosive", 5);

            rules.spend(c6, attack("PLANT", 0, 3));

            assertThat(c6.getQuantity()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Урон по площади")
    class Blast {

        @Test
        @DisplayName("Осколочная граната: зона 5 м, 7D6 (с. 63, 99)")
        void grenade() {
            Deque<Integer> queue = rolls(1, 2, 3, 4, 5, 6, 1);
            AreaDamage blast = Cp2020AreaAttacks.areaDamage(weapon("grenade_fragmentation", 1), attack("THROW", 5, 0), sides -> queue.removeFirst());

            assertThat(blast.radius()).isEqualTo(5);
            assertThat(blast.amount()).isEqualTo(22);
            assertThat(blast.data()).containsEntry("wholeBody", "false");
        }

        @Test
        @DisplayName("Взрывчатка: зона умножается на единицы, урон не растёт, по всему телу (с. 111)")
        void explosive() {
            Deque<Integer> queue = rolls(1, 1, 1, 1, 1, 1, 1, 1);
            AreaDamage blast = Cp2020AreaAttacks.areaDamage(weapon("c_6_plastic_explosive", 3), attack("PLANT", 0, 3), sides -> queue.removeFirst());

            assertThat(blast.radius()).isEqualTo(15);
            assertThat(blast.amount()).isEqualTo(8);
            assertThat(blast.data()).containsEntry("wholeBody", "true");
        }

        @Test
        @DisplayName("По всему телу: зона не бросается, считается торс")
        void wholeBodyTorso() {
            AreaDamage blast = new AreaDamage("C-6", "8d10", 30, 5, Map.of("damageType", "NORMAL", "wholeBody", "true"), List.of());

            AttackHit hit = Cp2020AreaAttacks.hit(blast, sides -> {
                throw new AssertionError("зона не бросается");
            });

            assertThat(hit.damage().getLocation()).isEqualTo("TORSO");
            assertThat(hit.damage().getAmount()).isEqualTo(30);
        }

        @Test
        @DisplayName("Осколки: зона у каждой цели своя")
        void ownLocation() {
            AreaDamage blast = new AreaDamage("Граната", "7d6", 20, 5, Map.of("damageType", "NORMAL"), List.of());

            assertThat(Cp2020AreaAttacks.hit(blast, sides -> 1).damage().getLocation()).isEqualTo("HEAD");
            assertThat(Cp2020AreaAttacks.hit(blast, sides -> 3).damage().getLocation()).isEqualTo("TORSO");
        }

        @Test
        @DisplayName("Газ не бросает урон: эффект применяет мастер")
        void effectOnly() {
            AreaDamage blast = Cp2020AreaAttacks.areaDamage(weapon("grenade_gas_nausea", 1), attack("THROW", 5, 0), sides -> {
                throw new AssertionError("урон не бросается");
            });

            assertThat(blast.amount()).isNull();
            AttackHit hit = Cp2020AreaAttacks.hit(blast, sides -> 1);
            assertThat(hit.damage()).isNull();
            assertThat(hit.steps()).anyMatch(step -> step.contains("мастер"));
        }

        @Test
        @DisplayName("Отклонение: направление и метры по таблице гранат (с. 99, 108)")
        void scatter() {
            AreaDamage blast = new AreaDamage("Граната", "7d6", 20, 5, Map.of("damageType", "NORMAL"), List.of());

            Deque<Integer> near = rolls(8, 4);
            assertThat(Cp2020AreaAttacks.scatter(blast, sides -> near.removeFirst()).steps())
                    .anyMatch(step -> step.contains("за цель") && step.contains("4 м"))
                    .anyMatch(step -> step.contains("всё ещё в зоне"));

            Deque<Integer> far = rolls(1, 9);
            assertThat(Cp2020AreaAttacks.scatter(blast, sides -> far.removeFirst()).steps())
                    .anyMatch(step -> step.contains("к стрелку"))
                    .anyMatch(step -> step.contains("вне зоны"));
        }
    }

    @Nested
    @DisplayName("Дробовик (с. 109)")
    class Shotgun {

        @ParameterizedTest
        @CsvSource({"5, 1, 4", "20, 2, 3", "40, 3, 2", "90, 3, 2"})
        @DisplayName("Пятно шире и кубиков меньше с расстоянием")
        void pattern(int distance, int radius, int dice) {
            Deque<Integer> queue = rolls(1, 1, 1, 1);
            AreaDamage blast = Cp2020AreaAttacks.areaDamage(weapon("arasaka_rapid_assault_12", 1), attack("SINGLE", distance, 0), sides -> queue.removeFirst());

            assertThat(blast.radius()).isEqualTo(radius);
            assertThat(blast.amount()).isEqualTo(dice);
        }

        @Test
        @DisplayName("Автодробовик: 5 пятен — один бросок с −8, как в примере книги")
        void autoshotgun() {
            Item caws = weapon("arasaka_rapid_assault_12", 1);

            CheckRequest check = rules.toHit(thrower, caws, attack("PATTERNS", 5, 5), STATS);

            assertThat(check.parts()).anyMatch(part -> part.value() == -8);
            Deque<Integer> queue = rolls(1, 1, 1, 1);
            assertThat(Cp2020AreaAttacks.areaDamage(caws, attack("PATTERNS", 5, 5), sides -> queue.removeFirst()).radius()).isEqualTo(5);
        }

        @Test
        @DisplayName("Пятен не больше ROF и заряда")
        void limit() {
            Item stakeout = weapon("sternmeyer_stakeout_10", 1);

            assertThatThrownBy(() -> rules.toHit(thrower, stakeout, attack("PATTERNS", 5, 2), STATS))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
