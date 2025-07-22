package app.charka.controller;

import app.charka.Routes;
import app.charka.model.Money;
import app.charka.service.MoneyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class MoneyControllerTest {

    private MoneyService moneyService;
    private MoneyController moneyController;

    @BeforeEach
    void setUp() {
        moneyService = mock(MoneyService.class);
        moneyController = new MoneyController(moneyService);
    }

    @Test
    void addMoney_success() {
        String result = moneyController.addMoney(1L, "Доход", 100L);

        assertEquals(Routes.CHARACTER_REDIRECT + 1, result);

        ArgumentCaptor<Money> captor = ArgumentCaptor.forClass(Money.class);
        verify(moneyService).create(eq(1L), captor.capture());

        Money saved = captor.getValue();
        assertEquals("Доход", saved.getName());
        assertEquals(100L, saved.getAmount());
        // operationDate is set to today
        assertEquals(LocalDate.now(), saved.getOperationDate());
    }

    @Test
    void addMoney_characterNotFound() {
        doThrow(new IllegalArgumentException("Character not found")).when(moneyService)
                .create(eq(99L), any(Money.class));

        assertThrows(IllegalArgumentException.class,
                () -> moneyController.addMoney(99L, "Расход", -50L));
    }

    @Test
    void deleteMoney_success() {
        String result = moneyController.deleteMoney(1L, 10L);

        assertEquals(Routes.CHARACTER_REDIRECT + 1, result);
        verify(moneyService).delete(10L);
    }
}
