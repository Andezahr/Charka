package app.charka.controller;

import app.charka.Routes;
import app.charka.model.Character;
import app.charka.model.Wound;
import app.charka.repository.CharacterRepository;
import app.charka.repository.WoundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;


class WoundControllerTest {

    private CharacterRepository characterRepository;
    private WoundRepository woundRepository;
    private WoundController woundController;

    @BeforeEach
    void setUp() {
        characterRepository = mock(CharacterRepository.class);
        woundRepository = mock(WoundRepository.class);
        woundController = new WoundController(characterRepository, woundRepository);
    }

    @Test
    void addWound_success() {
        Character character = new Character();
        character.setId(1L);
        when(characterRepository.findById(1L)).thenReturn(Optional.of(character));

        String result = woundController.addWound(1L, "Порез", 2L);

        assertEquals(Routes.CHARACTER_REDIRECT+1, result);

        ArgumentCaptor<Wound> woundCaptor = ArgumentCaptor.forClass(Wound.class);
        verify(woundRepository).save(woundCaptor.capture());
        Wound savedWound = woundCaptor.getValue();
        assertEquals("Порез", savedWound.getName());
        assertEquals(2L, savedWound.getSeverity());
        assertEquals(character, savedWound.getCharacter());
    }

    @Test
    void addWound_characterNotFound() {
        when(characterRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(Exception.class, () -> woundController.addWound(99L, "Порез", null));
    }

    @Test
    void deleteWound_success() {
        Character character = new Character();
        character.setId(1L);
        Wound wound = new Wound();
        wound.setId(5L);
        wound.setCharacter(character);

        when(woundRepository.findById(5L)).thenReturn(Optional.of(wound));

        String result = woundController.deleteWound(1L, 5L);

        assertEquals(Routes.CHARACTER_REDIRECT+1, result);
        verify(woundRepository).delete(wound);
    }

    @Test
    void deleteWound_wrongCharacter() {
        Character character = new Character();
        character.setId(2L);
        Wound wound = new Wound();
        wound.setId(6L);
        wound.setCharacter(character);

        when(woundRepository.findById(6L)).thenReturn(Optional.of(wound));

        String result = woundController.deleteWound(1L, 6L);

        assertEquals(Routes.CHARACTER_REDIRECT+1, result);
        verify(woundRepository, never()).delete(any());
    }
}