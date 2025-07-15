package app.Charka.controller;


import app.Charka.Routes;
import app.Charka.model.Character;
import app.Charka.repository.CharacterRepository;
import app.Charka.repository.MoneyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MoneyControllerTest {

    private MoneyRepository moneyRepository;
    private CharacterRepository characterRepository;
    private MoneyController moneyController;

    @BeforeEach
    void setUp() {
        moneyRepository = mock(MoneyRepository.class);
        characterRepository = mock(CharacterRepository.class);
        moneyController = new MoneyController(moneyRepository, characterRepository);
    }

    @Test
    void addMoney_success() {
        Character character = new Character();
        character.setId(1L);
        when(characterRepository.findById(1L)).thenReturn(Optional.of(character));

        String result = moneyController.addMoney(1L, "Доход", 100L);

        assertEquals(Routes.CHARACTER_REDIRECT+1, result);

        // Проверяем, что сохранён объект Money с нужными полями
        verify(moneyRepository).save(argThat(money ->
                "Доход".equals(money.getName()) &&
                        money.getAmount().equals(100L) &&
                        money.getCharacter() == character &&
                        money.getOperationDate().equals(LocalDate.now())
        ));
    }

    @Test
    void addMoney_characterNotFound() {
        when(characterRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(Exception.class, () -> moneyController.addMoney(99L, "Расход", -50L));
    }

    @Test
    void deleteMoney_success() {
        String result = moneyController.deleteMoney(1L, 10L);

        assertEquals(Routes.CHARACTER_REDIRECT+1, result);
        verify(moneyRepository).deleteById(10L);
    }
}