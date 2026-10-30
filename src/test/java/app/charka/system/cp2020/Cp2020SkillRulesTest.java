package app.charka.system.cp2020;

import app.charka.check.CheckRequest;
import app.charka.model.Character;
import app.charka.model.CharacterSkill;
import app.charka.skill.SkillDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CP2020 — связи навыков: бонусы, языки, требования")
class Cp2020SkillRulesTest {

    private final Character character = new Character();
    private final Cp2020SkillProgression progression = new Cp2020SkillProgression();

    private CharacterSkill add(Long id, String code, String customName, int level) {
        CharacterSkill skill = new CharacterSkill();
        skill.setId(id);
        skill.setCharacter(character);
        skill.setSkillCode(code);
        skill.setCustomName(customName);
        skill.setLevel(level);
        character.getSkills().add(skill);
        return skill;
    }

    private static SkillDefinition definition(String code) {
        return Cp2020Skills.CATALOG.stream().filter(skill -> skill.code().equals(code)).findFirst().orElseThrow();
    }

    @Nested
    @DisplayName("Языки")
    class LanguageTests {

        @ParameterizedTest(name = "{0} → ×{1}")
        @CsvSource({"language_germanic, 1", "language_romantic, 1", "language_semitic, 1", "language_african, 2",
                "language_baltic, 2", "language_celtic, 2", "language_farsi, 2", "language_greek, 2",
                "language_japanese, 2", "language_korean, 2", "language_pacific, 2", "language_sino_tibetan, 3",
                "language_slavic, 3"})
        @DisplayName("множитель зависит от семьи")
        void familyMultipliers(String code, int multiplier) {
            assertThat(definition(code).costMultiplier()).isEqualTo(multiplier);
            assertThat(definition(code).custom()).isTrue();
        }

        @Test
        @DisplayName("первый язык семьи — полная цена, следующие на 1 множитель дешевле")
        void discount() {
            CharacterSkill russian = add(1L, "language_slavic", "Русский", 4);
            CharacterSkill polish = add(2L, "language_slavic", "Польский", 3);

            assertThat(progression.cost(character, definition("language_slavic"), russian, 4)).isEqualTo(120);
            assertThat(progression.cost(character, definition("language_slavic"), polish, 3)).isEqualTo(60);
        }

        @Test
        @DisplayName("скидки нет, пока первый язык на нуле или он из другой семьи; меньше ×1 не бывает")
        void noDiscount() {
            add(1L, "language_slavic", "Русский", 0);
            add(2L, "language_japanese", "Японский", 5);
            CharacterSkill polish = add(3L, "language_slavic", "Польский", 3);
            add(4L, "language_germanic", "Английский", 5);
            CharacterSkill german = add(5L, "language_germanic", "Немецкий", 2);

            assertThat(progression.cost(character, definition("language_slavic"), polish, 3)).isEqualTo(90);
            assertThat(progression.cost(character, definition("language_germanic"), german, 2)).isEqualTo(20);
        }

        @Test
        @DisplayName("родственный язык даёт половину уровня, округление вниз, прибавка — только до неё")
        void halfLevel() {
            add(1L, "language_sino_tibetan", "Кантонский", 5);
            CharacterSkill mandarin = add(2L, "language_sino_tibetan", "Мандарин", 1);
            CharacterSkill thai = add(3L, "language_sino_tibetan", "Тайский", 4);

            assertThat(Cp2020SkillRules.bonuses(character, definition("language_sino_tibetan"), mandarin))
                    .containsExactly(new CheckRequest.Part("Родственный язык: Кантонский", 1));
            assertThat(Cp2020SkillRules.bonuses(character, definition("language_sino_tibetan"), thai)).isEmpty();
        }

        @Test
        @DisplayName("язык другой семьи ничего не даёт")
        void otherFamily() {
            add(1L, "language_germanic", "Английский", 8);
            CharacterSkill russian = add(2L, "language_slavic", "Русский", 0);

            assertThat(Cp2020SkillRules.bonuses(character, definition("language_slavic"), russian)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Бонусы")
    class BonusTests {

        @Test
        @DisplayName("Боевое чутьё прибавляется к Внимательности только у Соло")
        void combatSense() {
            add(1L, "combat_sense", null, 4);

            assertThat(Cp2020SkillRules.bonuses(character, definition("awareness"), null)).isEmpty();

            character.getRoles().add("solo");
            assertThat(Cp2020SkillRules.bonuses(character, definition("awareness"), null))
                    .containsExactly(new CheckRequest.Part("Боевое чутьё", 4));
            assertThat(Cp2020SkillRules.bonuses(character, definition("human_perception"), null)).isEmpty();
        }

        @ParameterizedTest(name = "Ораторство {0} → {1} бонусов")
        @CsvSource({"5, 0", "6, 1", "10, 1"})
        @DisplayName("Ораторство от 6 даёт +1 к Харизматичному лидерству")
        void oratory(int level, int expected) {
            add(1L, "oratory", null, level);

            assertThat(Cp2020SkillRules.bonuses(character, definition("charismatic_leadership"), null)).hasSize(expected);
        }
    }

    @Nested
    @DisplayName("Требования")
    class RequirementTests {

        @ParameterizedTest(name = "{0} требует {1} +4")
        @CsvSource({"physics, mathematics, Математика", "pharmaceuticals, chemistry, Химия"})
        @DisplayName("без нужного уровня повышать нельзя")
        void required(String code, String required, String name) {
            assertThat(progression.blockedReason(character, definition(code))).contains(name, "4");

            add(1L, required, null, 3);
            assertThat(progression.blockedReason(character, definition(code))).isNotNull();

            character.getSkills().getFirst().setLevel(4);
            assertThat(progression.blockedReason(character, definition(code))).isNull();
        }

        @Test
        @DisplayName("у остальных навыков требований нет")
        void noRequirement() {
            assertThat(progression.blockedReason(character, definition("handgun"))).isNull();
        }
    }
}
