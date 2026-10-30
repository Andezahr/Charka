package app.charka.system.cp2020;

import app.charka.model.Character;
import app.charka.model.counters.Counter;
import app.charka.skill.SkillDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CP2020 — очки улучшения")
class Cp2020IpTest {

    private final Cp2020SkillProgression progression = new Cp2020SkillProgression();
    private final Cp2020IpStrategy strategy = new Cp2020IpStrategy();

    @ParameterizedTest(name = "уровень {0}, множитель {1} → {2} IP")
    @CsvSource({"0, 1, 10", "1, 1, 10", "2, 1, 20", "4, 1, 40", "9, 1, 90", "0, 3, 30", "3, 2, 60", "9, 3, 270"})
    @DisplayName("цена: уровень × 10 × множитель, с нуля — как с первого")
    void cost(int level, int multiplier, int expected) {
        SkillDefinition definition = new SkillDefinition("x", "X", "ref", multiplier, false, false);

        assertThat(progression.cost(definition, level)).isEqualTo(expected);
    }

    @Test
    @DisplayName("баланс и списание — в счётчике CP2020_IP; нет счётчика — null")
    void balanceAndSpend() {
        Character character = new Character();
        assertThat(progression.balance(character)).isNull();

        Counter counter = new Counter();
        counter.setType(Cp2020IpStrategy.TYPE);
        counter.setValue(50);
        character.getCounters().add(counter);

        assertThat(progression.balance(character)).isEqualTo(50);
        progression.spend(character, 30);
        assertThat(counter.getValue()).isEqualTo(20);
    }

    @Test
    @DisplayName("счётчик: только CP2020, один на персонажа, в минус не уходит")
    void strategy() {
        assertThat(strategy.isAvailableIn(Cp2020System.CODE)).isTrue();
        assertThat(strategy.isAvailableIn("GENERIC")).isFalse();
        assertThat(strategy.isUniquePerCharacter()).isTrue();
        assertThat(strategy.getNewValue("10", 5)).isEqualTo("15");
        assertThat(strategy.getNewValue("10", -25)).isEqualTo("0");
    }
}
