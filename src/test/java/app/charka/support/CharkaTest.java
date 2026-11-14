package app.charka.support;

import app.charka.dice.DiceRoller;
import org.mockito.Answers;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * TT-01: общий контекст Spring для тестов без входа — один на весь прогон.
 * <p>
 * Всегда MockMvc и подменяемые кубики ({@link DiceRoller}): разные наборы аннотаций давали каждый свой контекст.
 * Кубики — мок, по умолчанию бросок 0; тест, которому важно число, задаёт его {@code when(dice.roll(10))}.
 * Со входом — {@link CharkaSecuredTest}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@MockitoBean(types = DiceRoller.class, answers = Answers.CALLS_REAL_METHODS)
public @interface CharkaTest {
}
