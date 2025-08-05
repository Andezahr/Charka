package app.charka.controller;

import app.charka.GlobalExceptionHandler;
import app.charka.model.Character;
import app.charka.model.Wound;
import app.charka.service.WoundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WoundController – unit-тесты через Standalone MockMvc")
class WoundControllerTest {

    // ======== DEPENDENCIES ========
    @Mock private WoundService woundService;
    @InjectMocks private WoundController woundController;

    // ======== INFRASTRUCTURE ========
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(woundController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ======================================================================
    //                            ADD WOUND BLOCK
    // ======================================================================
    @Nested
    @DisplayName("Добавление раны")
    class AddWound {

        private static final long CHAR_ID = 1L;

        @Test
        @DisplayName("302 Redirect – успешное добавление раны")
        void addWound_success() throws Exception {
            mockMvc.perform(post("/character/{id}/wounds", CHAR_ID)
                            .param("name", "Порез")
                            .param("severity", "2")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/character/1"));

            verify(woundService).create(eq(CHAR_ID), argThat(w ->
                    "Порез".equals(w.getName()) && Long.valueOf(2).equals(w.getSeverity())
            ));
            verifyNoMoreInteractions(woundService);
        }

        @Test
        @DisplayName("302 Redirect – добавление без severity")
        void addWound_withoutSeverity() throws Exception {
            mockMvc.perform(post("/character/{id}/wounds", CHAR_ID)
                            .param("name", "Царапина")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/character/1"));

            verify(woundService).create(eq(CHAR_ID), argThat(w ->
                    "Царапина".equals(w.getName()) && w.getSeverity() == null
            ));
            verifyNoMoreInteractions(woundService);
        }

        @Test
        @DisplayName("500 Internal Server Error – ошибка сервиса")
        void addWound_serviceThrows() throws Exception {
            doThrow(new RuntimeException("Character not found"))
                    .when(woundService).create(eq(99L), any());

            mockMvc.perform(post("/character/{id}/wounds", 99L)
                            .param("name", "Порез")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                    .andExpect(content().string("Character not found"));

            verify(woundService).create(eq(99L), any());
            verifyNoMoreInteractions(woundService);
        }
    }

    // ======================================================================
    //                           DELETE WOUND BLOCK
    // ======================================================================
    @Nested
    @DisplayName("Удаление раны")
    class DeleteWound {

        private static final long CHAR_ID = 1L;
        private static final long OTHER_CHAR_ID = 2L;

        @Test
        @DisplayName("302 Redirect – успешное удаление раны")
        void deleteWound_success() throws Exception {

            Character character = new Character();
            character.setId(CHAR_ID);
            Wound wound = new Wound();
            wound.setId(5L);
            wound.setCharacter(character);

            when(woundService.getById(5L)).thenReturn(wound);

            mockMvc.perform(post("/character/{charId}/wounds/{woundId}/delete", CHAR_ID, 5L))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/character/1"));

            verify(woundService).getById(5L);
            verify(woundService).delete(5L);
            verifyNoMoreInteractions(woundService);
        }

        @Test
        @DisplayName("302 Redirect – попытка удалить рану другого персонажа")
        void deleteWound_wrongCharacter() throws Exception {
            Character otherCharacter = new Character();
            otherCharacter.setId(OTHER_CHAR_ID);
            Wound foreignWound = new Wound();
            foreignWound.setId(6L);
            foreignWound.setCharacter(otherCharacter);

            when(woundService.getById(6L)).thenReturn(foreignWound);

            mockMvc.perform(post("/character/{charId}/wounds/{woundId}/delete", CHAR_ID, 6L))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/character/1"));

            verify(woundService).getById(6L);
            verify(woundService, never()).delete(anyLong());
            verifyNoMoreInteractions(woundService);
        }

        @Test
        @DisplayName("500 Internal Server Error – рана не найдена")
        void deleteWound_notFound() throws Exception {
            when(woundService.getById(99L))
                    .thenThrow(new IllegalArgumentException("Wound not found for id=99"));


            mockMvc.perform(post("/character/{charId}/wounds/{woundId}/delete", CHAR_ID, 99L))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                    .andExpect(content().string("Wound not found for id=99"));

            verify(woundService).getById(99L);
            verifyNoMoreInteractions(woundService);
        }
    }
}
