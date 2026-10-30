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
        @DisplayName("множители по книге: скрытность ×2, гирокоптер ×3, боевые искусства — по стилю")
        void costMultipliers() {
            assertThat(cost("stealth")).isEqualTo(2);
            assertThat(cost("pilot_gyro")).isEqualTo(3);
            assertThat(cost("handgun")).isEqualTo(1);
            assertThat(cost("boxing")).isEqualTo(1);
            assertThat(cost("karate")).isEqualTo(2);
            assertThat(cost("choi_li_fut")).isEqualTo(3);
            assertThat(cost("thai_kick_boxing")).isEqualTo(4);
        }

        @Test
        @DisplayName("статы спецспособностей по книге: Ресурсы — INT, Авторитет — COOL")
        void specialStats() {
            assertThat(stat("resources")).isEqualTo("int");
            assertThat(stat("authority")).isEqualTo("cool");
        }

        private String stat(String code) {
            return Cp2020Skills.CATALOG.stream().filter(s -> s.code().equals(code)).findFirst().orElseThrow()
                    .statCode();
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
                "handgun, COMBAT", "martial_art, COMBAT", "karate, COMBAT", "athletics, ATHLETICS", "swimming, ATHLETICS",
                "basic_tech, TECH", "first_aid, TECH", "diagnose, INTELLECT", "seduction, EMPATHY",
                "intimidate, EMPATHY", "resources, EMPATHY", "awareness, INTELLECT"})
        @DisplayName("таблица зависит от навыка")
        void tableBySkill(String code, Cp2020Fumbles.Table table) {
            assertThat(Cp2020Fumbles.tableFor(code)).isEqualTo(table);
        }

        @Test
        @DisplayName("свободная проверка и неизвестный навык — раздела нет")
        void noTable() {
            assertThat(Cp2020Fumbles.tableFor(null)).isNull();
            assertThat(Cp2020Fumbles.tableFor("unknown")).isNull();
        }

        @ParameterizedTest(name = "{0}, d10 = {1}: {2}")
        @CsvSource({"ATHLETICS, 5, 1 очко", "ATHLETICS, 8, 1d6", "TECH, 7, сложность +5", "TECH, 8, нужна новая",
                "EMPATHY, 6, −4", "EMPATHY, 7, нападает", "INTELLECT, 7, −2", "INTELLECT, 8, видят все"})
        @DisplayName("диапазоны остальных разделов — как в книге")
        void otherRanges(Cp2020Fumbles.Table table, int roll, String expected) {
            assertThat(table.outcome(roll)).contains(expected);
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
