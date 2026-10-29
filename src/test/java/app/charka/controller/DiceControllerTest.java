package app.charka.controller;

import app.charka.dice.DiceExpression;
import app.charka.dice.DiceResult;
import app.charka.dice.DiceService;
import app.charka.exception.CharacterNotFoundException;
import app.charka.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DiceController – броски с листа и со страницы кампании")
class DiceControllerTest {

    @Mock private DiceService diceService;
    @InjectMocks private DiceController diceController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(diceController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static DiceResult result() {
        return DiceResult.of(DiceExpression.parse("2d6"), List.of(3, 4));
    }

    @Test
    @DisplayName("бросок из поля — результат и подпись во flash, редирект к кубикам")
    void rollsExpression() throws Exception {
        when(diceService.rollForCharacter(1L, "2d6", "Атака")).thenReturn(result());

        mockMvc.perform(post("/character/1/roll").param("expression", "2d6").param("label", "Атака"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/character/1#dice"))
                .andExpect(flash().attribute("rollResult", result()))
                .andExpect(flash().attribute("rollLabel", "Атака"));
    }

    @Test
    @DisplayName("быстрая кнопка важнее поля")
    void quickWins() throws Exception {
        when(diceService.rollForCharacter(eq(1L), eq("d10"), any())).thenReturn(result());

        mockMvc.perform(post("/character/1/roll").param("expression", "").param("quick", "d10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("rollResult"))
                .andExpect(flash().attributeCount(1));
    }

    @Test
    @DisplayName("непонятное выражение — rollError во flash, не страница ошибки")
    void badExpression() throws Exception {
        when(diceService.rollForCampaign(5L, "abc", null)).thenThrow(new IllegalArgumentException("Не понимаю бросок"));

        mockMvc.perform(post("/campaign/5/roll").param("expression", "abc"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/campaign/5#dice"))
                .andExpect(flash().attribute("rollError", "Не понимаю бросок"));
    }

    @Test
    @DisplayName("неизвестный персонаж — 404")
    void unknownCharacter() throws Exception {
        when(diceService.rollForCharacter(eq(99L), any(), any()))
                .thenThrow(new CharacterNotFoundException("Character not found for ID = 99"));

        mockMvc.perform(post("/character/99/roll").param("expression", "d6"))
                .andExpect(status().isNotFound());
    }
}
