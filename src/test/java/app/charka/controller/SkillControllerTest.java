package app.charka.controller;

import app.charka.check.CheckResult;
import app.charka.exception.CharacterNotFoundException;
import app.charka.exception.GlobalExceptionHandler;
import app.charka.model.Character;
import app.charka.service.CharacterService;
import app.charka.skill.SkillCheck;
import app.charka.skill.SkillService;
import app.charka.system.GameSystemRegistry;
import app.charka.system.GenericSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SkillController — страница навыков и роль")
class SkillControllerTest {

    @Mock private SkillService skillService;
    @Mock private CharacterService characterService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        GameSystemRegistry registry = new GameSystemRegistry(List.of(new GenericSystem()));
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SkillController(skillService, characterService, registry))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("система без навыков — обратно на лист")
    void noCatalogRedirects() throws Exception {
        when(characterService.getById(1L)).thenReturn(new Character());
        when(skillService.hasCatalog(1L)).thenReturn(false);

        mockMvc.perform(get("/character/1/skills"))
                .andExpect(redirectedUrl("/character/1"));
    }

    @Test
    @DisplayName("неизвестный персонаж — 404")
    void unknownCharacter() throws Exception {
        when(characterService.getById(9L)).thenThrow(new CharacterNotFoundException("Character not found"));

        mockMvc.perform(get("/character/9/skills"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("уровни разбираются из полей level.* и custom.*, пустое поле — 0")
    void updatesLevels() throws Exception {
        mockMvc.perform(post("/character/1/skills")
                        .param("level.handgun", "4").param("level.rifle", "").param("custom.10", "7"))
                .andExpect(redirectedUrl("/character/1/skills"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(skillService).updateLevels(1L, Map.of("handgun", 4, "rifle", 0), Map.of(10L, 7));
    }

    @Test
    @DisplayName("буквы в уровне — errorMessage, сохранения нет")
    void badLevel() throws Exception {
        mockMvc.perform(post("/character/1/skills").param("level.handgun", "abc"))
                .andExpect(redirectedUrl("/character/1/skills"))
                .andExpect(flash().attributeExists("errorMessage"));

        verifyNoInteractions(skillService);
    }

    @Test
    @DisplayName("ошибка сервиса при добавлении навыка — errorMessage")
    void customRejected() throws Exception {
        doThrow(new IllegalArgumentException("Уже есть"))
                .when(skillService).addCustom(1L, "language", "Русский", 8);

        mockMvc.perform(post("/character/1/skills/custom")
                        .param("code", "language").param("customName", "Русский").param("level", "8"))
                .andExpect(flash().attribute("errorMessage", "Уже есть"));
    }

    @Test
    @DisplayName("удаление навыка и смена роли")
    void deleteAndRole() throws Exception {
        mockMvc.perform(post("/character/1/skills/custom/10/delete"))
                .andExpect(redirectedUrl("/character/1/skills"));
        verify(skillService).deleteCustom(1L, 10L);

        mockMvc.perform(post("/character/1/role").param("role", "solo"))
                .andExpect(redirectedUrl("/character/1/skills"))
                .andExpect(flash().attributeExists("successMessage"));
        verify(skillService).setRole(1L, "solo");
    }

    @Test
    @DisplayName("проверка — результат и подпись во flash")
    void check() throws Exception {
        CheckResult result = new CheckResult("d10!+12", List.of("Бросок d10: 7"), 19, 15, true, false, false);
        when(skillService.check(1L, "handgun", null, -1, 15)).thenReturn(new SkillCheck("Пистолеты", result));

        mockMvc.perform(post("/character/1/skills/check")
                        .param("code", "handgun").param("skillId", "")
                        .param("modifier", "-1").param("difficulty", "15"))
                .andExpect(redirectedUrl("/character/1/skills"))
                .andExpect(flash().attribute("checkResult", result))
                .andExpect(flash().attribute("rollLabel", "Пистолеты"));
    }

    @Test
    @DisplayName("проверка неизвестного навыка — errorMessage, а не страница ошибки")
    void checkRejected() throws Exception {
        when(skillService.check(eq(1L), eq("nope"), any(), eq(0), any()))
                .thenThrow(new IllegalArgumentException("Неизвестный навык"));

        mockMvc.perform(post("/character/1/skills/check").param("code", "nope"))
                .andExpect(redirectedUrl("/character/1/skills"))
                .andExpect(flash().attribute("errorMessage", "Неизвестный навык"));
    }
}
