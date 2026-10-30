package app.charka.api;

import app.charka.check.CheckResult;
import app.charka.exception.ApiExceptionHandler;
import app.charka.exception.CharacterNotFoundException;
import app.charka.check.OpposedCheckResult;
import app.charka.skill.SkillCheck;
import app.charka.skill.SkillOpponent;
import app.charka.skill.SkillService;
import app.charka.skill.SkillSheet;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SkillCheckController — проверка навыка без перезагрузки страницы")
class SkillCheckControllerTest {

    private static final String URL = "/api/characters/1/skills/check";

    @Mock private SkillService skillService;
    @InjectMocks private SkillCheckController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("итог, короткий результат и шаги — в JSON")
    void returnsResult() throws Exception {
        CheckResult result = new CheckResult("d10!+12", List.of("Бросок d10: 7", "Сложность 15 → успех"),
                19, 15, true, false, false);
        when(skillService.check(1L, "handgun", null, -1, 15)).thenReturn(new SkillCheck("Пистолеты", result));

        mockMvc.perform(post(URL).param("code", "handgun").param("modifier", "-1").param("difficulty", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Пистолеты"))
                .andExpect(jsonPath("$.total").value(19))
                .andExpect(jsonPath("$.outcome").value("19 vs 15 — успех"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.steps[1]").value("Сложность 15 → успех"));
    }

    @Test
    @DisplayName("навык со своим названием — по id строки, без сложности успех не определён")
    void customWithoutDifficulty() throws Exception {
        CheckResult result = new CheckResult("d10!+3", List.of("Бросок d10: 4"), 7, null, null, false, false);
        when(skillService.check(1L, null, 10L, 0, null)).thenReturn(new SkillCheck("Язык: Русский", result));

        mockMvc.perform(post(URL).param("skillId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    @DisplayName("неизвестный навык — 400 с текстом ошибки для диалога")
    void badSkill() throws Exception {
        when(skillService.check(eq(1L), eq("nope"), any(), eq(0), any()))
                .thenThrow(new IllegalArgumentException("Неизвестный навык: nope"));

        mockMvc.perform(post(URL).param("code", "nope"))
                .andExpect(status().isBadRequest())
                .andExpect(content().encoding("UTF-8"))
                .andExpect(content().string("Неизвестный навык: nope"));
    }

    @Test
    @DisplayName("неизвестный персонаж — 404")
    void unknownCharacter() throws Exception {
        when(skillService.check(eq(1L), any(), any(), eq(0), any()))
                .thenThrow(new CharacterNotFoundException("Character not found"));

        mockMvc.perform(post(URL).param("code", "handgun"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("противопоставленная проверка: оба участника, победитель и итог в JSON")
    void opposed() throws Exception {
        CheckResult own = new CheckResult("d10!+12", List.of("Бросок d10: 7"), 19, null, null, false, false);
        CheckResult other = new CheckResult("d10!+12", List.of("Бросок d10: 5"), 17, null, null, false, false);
        when(skillService.checkOpposed(1L, "handgun", null, 0, new SkillOpponent(2L, "dodge", null, null, -1)))
                .thenReturn(new OpposedCheckResult(
                        new OpposedCheckResult.Side("Колясик", "Пистолеты", own),
                        new OpposedCheckResult.Side("Олежа", "Уклонение", other),
                        OpposedCheckResult.Winner.INITIATOR, "Победа: Колясик (19 против 17)"));

        mockMvc.perform(post(URL + "/opposed").param("code", "handgun").param("opponentId", "2")
                        .param("opponentCode", "dodge").param("opponentModifier", "-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.winner").value("INITIATOR"))
                .andExpect(jsonPath("$.verdict").value("Победа: Колясик (19 против 17)"))
                .andExpect(jsonPath("$.initiator.label").value("Колясик · Пистолеты"))
                .andExpect(jsonPath("$.initiator.success").value(true))
                .andExpect(jsonPath("$.opponent.total").value(17))
                .andExpect(jsonPath("$.opponent.success").value(false));
    }

    @Test
    @DisplayName("противник из другой кампании — 400 с текстом ошибки")
    void opposedBadOpponent() throws Exception {
        when(skillService.checkOpposed(eq(1L), eq("handgun"), any(), eq(0), any()))
                .thenThrow(new IllegalArgumentException("Противник должен быть из той же кампании"));

        mockMvc.perform(post(URL + "/opposed").param("code", "handgun").param("opponentId", "9"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Противник должен быть из той же кампании"));
    }

    @Test
    @DisplayName("список навыков: все группы подряд, уровень и итог")
    void skillOptions() throws Exception {
        SkillSheet.Row row = new SkillSheet.Row("handgun", null, "Пистолеты", 4, 12, "REF", 8, null, null, false);
        SkillSheet.Group group = new SkillSheet.Group("REF", null, false, List.of(), List.of(row));
        when(skillService.getSheet(1L)).thenReturn(
                new SkillSheet(List.of(), List.of(), null, List.of(group), false, null, null));

        mockMvc.perform(get("/api/characters/1/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("handgun"))
                .andExpect(jsonPath("$[0].level").value(4))
                .andExpect(jsonPath("$[0].total").value(12));
    }

    @Test
    @DisplayName("буквы в числовом поле — 400, а не 500")
    void badNumber() throws Exception {
        mockMvc.perform(post(URL).param("code", "handgun").param("modifier", "abc"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(skillService);
    }
}
