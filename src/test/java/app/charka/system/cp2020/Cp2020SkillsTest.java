package app.charka.system.cp2020;

import app.charka.skill.CharacterRole;
import app.charka.skill.SkillDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CP2020 — каталог навыков, роли, фамблы")
class Cp2020SkillsTest {

    @Nested
    @DisplayName("Каталог")
    class CatalogTests {

        @Test
        @DisplayName("коды навыков уникальны")
        void uniqueCodes() {
            Set<String> codes = Cp2020Skills.CATALOG.stream().map(SkillDefinition::code).collect(Collectors.toSet());

            assertThat(codes).hasSameSizeAs(Cp2020Skills.CATALOG);
        }

        @Test
        @DisplayName("у каждой из 10 ролей есть своя спецспособность в каталоге")
        void rolesHaveSpecials() {
            Set<String> specials = Cp2020Skills.CATALOG.stream()
                    .filter(SkillDefinition::special).map(SkillDefinition::code).collect(Collectors.toSet());

            assertThat(Cp2020Skills.ROLES).hasSize(10);
            assertThat(Cp2020Skills.ROLES.stream().map(CharacterRole::specialSkillCode).collect(Collectors.toSet()))
                    .isEqualTo(specials);
        }

        @Test
        @DisplayName("навыки ссылаются на существующие статы")
        void knownStats() {
            assertThat(Cp2020Skills.CATALOG).extracting(SkillDefinition::statCode)
                    .allMatch(Set.of("attr", "body", "cool", "emp", "int", "ref", "tech")::contains);
        }

        @Test
        @DisplayName("сложные навыки дороже: боевое искусство ×2, гирокоптер ×3")
        void costMultipliers() {
            assertThat(cost("martial_art")).isEqualTo(2);
            assertThat(cost("pilot_gyro")).isEqualTo(3);
            assertThat(cost("handgun")).isEqualTo(1);
        }

        private int cost(String code) {
            return Cp2020Skills.CATALOG.stream().filter(s -> s.code().equals(code)).findFirst().orElseThrow()
                    .costMultiplier();
        }
    }

    @Nested
    @DisplayName("Фамблы")
    class FumbleTests {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
                "handgun, COMBAT", "martial_art, COMBAT", "athletics, REFLEX", "swimming, REFLEX",
                "basic_tech, TECH", "first_aid, MEDICAL", "diagnose, MEDICAL", "seduction, SOCIAL",
                "intimidate, SOCIAL", "awareness, INTELLECT", "unknown, GENERAL"})
        @DisplayName("таблица зависит от навыка")
        void tableBySkill(String code, Cp2020Fumbles.Table table) {
            assertThat(Cp2020Fumbles.tableFor(code)).isEqualTo(table);
        }

        @Test
        @DisplayName("свободная проверка — общая таблица")
        void freeCheck() {
            assertThat(Cp2020Fumbles.tableFor(null)).isEqualTo(Cp2020Fumbles.Table.GENERAL);
        }

        @ParameterizedTest(name = "d10 = {0}: {1}")
        @CsvSource({"1, обошлось", "4, обошлось", "5, выпало", "7, заклинило", "8, себя", "10, союзника"})
        @DisplayName("боевая таблица: диапазоны бросков")
        void combatRanges(int roll, String expected) {
            assertThat(Cp2020Fumbles.Table.COMBAT.outcome(roll)).contains(expected);
        }

        @Test
        @DisplayName("1–4 в каждой таблице — без последствий")
        void lowRollsHarmless() {
            for (Cp2020Fumbles.Table table : Cp2020Fumbles.Table.values()) {
                assertThat(table.outcome(4)).startsWith("обошлось");
                assertThat(table.outcome(5)).doesNotStartWith("обошлось");
                assertThat(table.outcome(10)).isNotBlank();
            }
        }
    }
}
