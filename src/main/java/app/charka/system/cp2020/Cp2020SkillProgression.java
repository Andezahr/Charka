package app.charka.system.cp2020;

import app.charka.model.Character;
import app.charka.model.counters.Counter;
import app.charka.skill.SkillDefinition;
import app.charka.skill.SkillProgression;

import java.util.Comparator;
import java.util.Optional;

/**
 * Прокачка CP2020 (книга, с. 53–54): первый уровень всегда стоит 10 IP, следующие — «текущий уровень × 10 × множитель навыка».
 * Спецспособности ролей — по той же формуле.
 */
public class Cp2020SkillProgression implements SkillProgression {

    static final int COST_PER_LEVEL = 10;

    @Override
    public String getPointsName() {
        return "IP";
    }

    @Override
    public int cost(SkillDefinition definition, int level) {
        return level == 0 ? COST_PER_LEVEL : level * COST_PER_LEVEL * definition.costMultiplier();
    }

    @Override
    public Integer balance(Character character) {
        return ipCounter(character)
                .map(counter -> counter.getValue() == null ? 0 : counter.getValue())
                .orElse(null);
    }

    @Override
    public void spend(Character character, int amount) {
        Counter counter = ipCounter(character)
                .orElseThrow(() -> new IllegalStateException("У персонажа нет счётчика очков улучшения"));
        counter.setValue(counter.getValue() - amount);
    }

    private static Optional<Counter> ipCounter(Character character) {
        return character.getCounters().stream()
                .filter(counter -> Cp2020IpStrategy.TYPE.equals(counter.getType()))
                .min(Comparator.comparing(Counter::getId, Comparator.nullsLast(Comparator.naturalOrder())));
    }
}
