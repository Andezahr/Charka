package app.charka.support;

import app.charka.model.counters.strategy.health.DiceRoller;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

import java.lang.reflect.Field;
import java.util.concurrent.ThreadLocalRandom;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * TT-01: кубики в общем контексте — мок ({@link CharkaTest}). Тест, который сам берёт {@link DiceRoller} полем, задаёт
 * броски сам (незаданный — 0, как раньше с {@code @MockitoBean}); остальным мок бросает по-настоящему случайно.
 */
public class RandomDiceListener extends AbstractTestExecutionListener {

    @Override
    public void beforeTestMethod(TestContext testContext) {
        if (declaresDice(testContext.getTestClass())) {
            return;
        }
        DiceRoller dice = testContext.getApplicationContext().getBean(DiceRoller.class);
        when(dice.roll(anyInt())).thenAnswer(call -> 1 + ThreadLocalRandom.current().nextInt((int) call.getArgument(0)));
    }

    private static boolean declaresDice(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getEnclosingClass()) {
            for (Class<?> level = current; level != null && level != Object.class; level = level.getSuperclass()) {
                for (Field field : level.getDeclaredFields()) {
                    if (DiceRoller.class.isAssignableFrom(field.getType())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** После внедрения моков и до транзакции теста */
    @Override
    public int getOrder() {
        return 3000;
    }
}
