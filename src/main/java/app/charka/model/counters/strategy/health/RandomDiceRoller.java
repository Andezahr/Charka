package app.charka.model.counters.strategy.health;

import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class RandomDiceRoller implements DiceRoller {

    @Override
    public int roll(int sides) {
        return ThreadLocalRandom.current().nextInt(1, sides + 1);
    }
}
