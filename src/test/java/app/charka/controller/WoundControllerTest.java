package app.charka.controller;

import app.charka.Routes;
import app.charka.model.Character;
import app.charka.model.Wound;
import app.charka.service.WoundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WoundControllerTest {

    private WoundService woundService;
    private WoundController woundController;

    @BeforeEach
    void setUp() {
        woundService = mock(WoundService.class);
        woundController = new WoundController(woundService);
    }

    @Test
    void addWound_success() {
        String result = woundController.addWound(1L, "Порез", 2L);

        assertEquals(Routes.CHARACTER_REDIRECT + 1, result);

        ArgumentCaptor<Wound> woundCaptor = ArgumentCaptor.forClass(Wound.class);
        verify(woundService).create(eq(1L), woundCaptor.capture());
        Wound capturedWound = woundCaptor.getValue();
        assertEquals("Порез", capturedWound.getName());
        assertEquals(2L, capturedWound.getSeverity());
    }

    @Test
    void addWound_withoutSeverity() {
        String result = woundController.addWound(1L, "Царапина", null);

        assertEquals(Routes.CHARACTER_REDIRECT + 1, result);

        ArgumentCaptor<Wound> woundCaptor = ArgumentCaptor.forClass(Wound.class);
        verify(woundService).create(eq(1L), woundCaptor.capture());
        Wound capturedWound = woundCaptor.getValue();
        assertEquals("Царапина", capturedWound.getName());
        assertNull(capturedWound.getSeverity());
    }

    @Test
    void addWound_serviceThrowsException() {
        doThrow(new RuntimeException("Character not found")).when(woundService).create(eq(99L), any());

        assertThrows(RuntimeException.class, () -> woundController.addWound(99L, "Порез", null));
    }

    @Test
    void deleteWound_success() {
        Character character = new Character();
        character.setId(1L);
        Wound wound = new Wound();
        wound.setId(5L);
        wound.setCharacter(character);

        when(woundService.getById(5L)).thenReturn(Optional.of(wound));

        String result = woundController.deleteWound(1L, 5L);

        assertEquals(Routes.CHARACTER_REDIRECT + 1, result);
        verify(woundService).delete(5L);
    }

    @Test
    void deleteWound_wrongCharacter() {
        Character character = new Character();
        character.setId(2L);
        Wound wound = new Wound();
        wound.setId(6L);
        wound.setCharacter(character);

        when(woundService.getById(6L)).thenReturn(Optional.of(wound));

        String result = woundController.deleteWound(1L, 6L);

        assertEquals(Routes.CHARACTER_REDIRECT + 1, result);
        verify(woundService, never()).delete(any());
    }

    @Test
    void deleteWound_woundNotFound() {
        when(woundService.getById(99L)).thenReturn(Optional.empty());

        assertThrows(Exception.class, () -> woundController.deleteWound(1L, 99L));
    }
}
