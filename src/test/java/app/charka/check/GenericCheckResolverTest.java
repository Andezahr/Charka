package app.charka.check;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GenericCheckResolver — d20 + модификатор против сложности")
class GenericCheckResolverTest {

    private final GenericCheckResolver resolver = new GenericCheckResolver();

    @Test
    @DisplayName("итог, успех и шаги; ни критов, ни провалов по кубику")
    void resolves() {
        CheckResult result = resolver.resolve(new CheckRequest(null, -2, 15), sides -> 20);

        assertThat(result.total()).isEqualTo(18);
        assertThat(result.success()).isTrue();
        assertThat(result.critical()).isFalse();
        assertThat(result.fumble()).isFalse();
        assertThat(result.notation()).isEqualTo("d20-2");
        assertThat(result.steps()).containsExactly("Бросок d20: 20", "Модификатор -2 → 18", "Сложность 15 → успех");
    }

    @Test
    @DisplayName("ровно сложность — успех, на единицу меньше — провал")
    void boundary() {
        assertThat(resolver.resolve(new CheckRequest(null, 0, 10), sides -> 10).success()).isTrue();
        assertThat(resolver.resolve(new CheckRequest(null, 0, 10), sides -> 9).success()).isFalse();
    }

    @Test
    @DisplayName("без модификатора и сложности — один шаг")
    void bare() {
        CheckResult result = resolver.resolve(new CheckRequest(null, 0, null), sides -> 1);

        assertThat(result.steps()).containsExactly("Бросок d20: 1");
        assertThat(result.success()).isNull();
        assertThat(result.notation()).isEqualTo("d20");
    }
}
