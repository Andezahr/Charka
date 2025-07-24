package app.charka.controller;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import app.charka.GlobalExceptionHandler;
import app.charka.model.Money;
import app.charka.service.MoneyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;


@ExtendWith(MockitoExtension.class)
@DisplayName("MoneyController – unit-тесты через Standalone MockMvc")
class MoneyControllerTest {

    // ======== DEPENDENCIES===
    @Mock private MoneyService moneyService;
    @InjectMocks private MoneyController moneyController;

    // ======== INFRASTRUCTURE ========
    private MockMvc mockMvc;

    // ======== CONSTANTS ========
    private static final long CHAR_ID         = 1L;
    private static final long MISSING_CHAR_ID = 99L;
    private static final long MONEY_ID        = 10L;
    private static final String NAME_INCOME   = "Доход";
    private static final String NAME_EXPENSE  = "Расход";
    private static final String REDIRECT_URL  = "/character/" + CHAR_ID;
    private static final String ERROR_MSG     = "Character not found";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(moneyController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ======================================================================
    //                            ADD MONEY BLOCK
    // ======================================================================
    @Nested
    @DisplayName("Добавление денег")
    class AddMoneyTests {

        @Test
        @DisplayName("302 Redirect – успешное добавление дохода")
        void addIncome_success() throws Exception {
            mockMvc.perform(post("/character/{characterId}/money", CHAR_ID)
                            .param("name", NAME_INCOME)
                            .param("amount", "100")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(REDIRECT_URL));

            ArgumentCaptor<Money> captor = ArgumentCaptor.forClass(Money.class);
            verify(moneyService).create(eq(CHAR_ID), captor.capture());
            Money saved = captor.getValue();

            assertAll("Поля Money при добавлении дохода",
                    () -> assertEquals(NAME_INCOME, saved.getName()),
                    () -> assertEquals(100L, saved.getAmount()),
                    () -> assertEquals(LocalDate.now(), saved.getOperationDate())
            );
            verifyNoMoreInteractions(moneyService);
        }

        @Test
        @DisplayName("302 Redirect – успешное добавление расхода")
        void addExpense_success() throws Exception {
            mockMvc.perform(post("/character/{characterId}/money", CHAR_ID)
                            .param("name", NAME_EXPENSE)
                            .param("amount", "-50")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(REDIRECT_URL));

            ArgumentCaptor<Money> captor = ArgumentCaptor.forClass(Money.class);
            verify(moneyService).create(eq(CHAR_ID), captor.capture());
            Money saved = captor.getValue();

            assertAll("Поля Money при добавлении расхода",
                    () -> assertEquals(NAME_EXPENSE, saved.getName()),
                    () -> assertEquals(-50L, saved.getAmount()),
                    () -> assertEquals(LocalDate.now(), saved.getOperationDate())
            );
            verifyNoMoreInteractions(moneyService);
        }

        @Test
        @DisplayName("500 Internal Server Error – персонаж не найден")
        void addMoney_characterNotFound() throws Exception {
            doThrow(new IllegalArgumentException(ERROR_MSG))
                    .when(moneyService).create(eq(MISSING_CHAR_ID), any());

            mockMvc.perform(post("/character/{characterId}/money", MISSING_CHAR_ID)
                            .param("name", NAME_EXPENSE)
                            .param("amount", "-50")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                    .andExpect(content().string(ERROR_MSG));

            verify(moneyService).create(eq(MISSING_CHAR_ID), any());
            verifyNoMoreInteractions(moneyService);
        }
    }

    // ======================================================================
    //                           DELETE MONEY BLOCK
    // ======================================================================
    @Nested
    @DisplayName("Удаление денег")
    class DeleteMoneyTests {

        @Test
        @DisplayName("302 Redirect – успешное удаление по ID")
        void deleteMoney_success() throws Exception {
            mockMvc.perform(post("/character/{characterId}/money/{moneyId}/delete",
                            CHAR_ID, MONEY_ID))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(REDIRECT_URL));

            verify(moneyService).delete(MONEY_ID);
            verifyNoMoreInteractions(moneyService);
        }

        @Test
        @DisplayName("500 Internal Server Error – ошибка удаления")
        void deleteMoney_error() throws Exception {
            doThrow(new RuntimeException("Delete error"))
                    .when(moneyService).delete(MONEY_ID);

            mockMvc.perform(post("/character/{characterId}/money/{moneyId}/delete",
                            CHAR_ID, MONEY_ID))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentTypeCompatibleWith(String.valueOf(MediaType.TEXT_PLAIN)))
                    .andExpect(content().string("Delete error"));

            verify(moneyService).delete(MONEY_ID);
            verifyNoMoreInteractions(moneyService);
        }
    }
}
