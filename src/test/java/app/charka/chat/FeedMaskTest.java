package app.charka.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Лента для игрока: враги в тумане — «?» (M-16, J-01)")
class FeedMaskTest {

    @Test
    @DisplayName("Скрытое имя заменяется целиком, видимое с тем же началом не трогается")
    void mask() {
        var mask = ChatService.mask(Map.of("NPC", false, "NPC 1", true, "Олежа", false));

        assertThat(mask.apply("Avenger → NPC 1: Мимо")).isEqualTo("Avenger → ?: Мимо");
        assertThat(mask.apply("NPC получает 4 урона")).isEqualTo("NPC получает 4 урона");
        assertThat(mask.apply("dmg:NPC 1: 4 урона\nhit:Попал")).isEqualTo("dmg:?: 4 урона\nhit:Попал");
        assertThat(mask.apply(null)).isNull();
    }
}
