package app.charka.service;

import app.charka.service.impl.MoneyServiceImpl;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Money;
import app.charka.repository.CharacterRepository;
import app.charka.repository.MoneyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MoneyService")
class MoneyServiceTest {

    private static final long CHAR_ID = 1L;
    private static final LocalDate GAME_DATE = LocalDate.of(2037, 10, 27);

    @Mock private MoneyRepository moneyRepository;
    @Mock private CharacterRepository characterRepository;
    @InjectMocks private MoneyServiceImpl moneyService;

    @Nested
    @DisplayName("Дата операции при создании")
    class CreateDateTests {

        @Test
        @DisplayName("без даты — ставится игровая дата кампании")
        void create_usesCampaignCurrentDate() {
            Campaign campaign = new Campaign();
            campaign.setCurrentDate(GAME_DATE);
            givenCharacter(campaign);

            Money saved = moneyService.create(CHAR_ID, money(null));

            assertThat(saved.getOperationDate()).isEqualTo(GAME_DATE);
        }

        @Test
        @DisplayName("персонаж без кампании — реальная дата")
        void create_withoutCampaign_usesToday() {
            givenCharacter(null);

            Money saved = moneyService.create(CHAR_ID, money(null));

            assertThat(saved.getOperationDate()).isEqualTo(LocalDate.now());
        }

        @Test
        @DisplayName("явно заданная дата не перезаписывается")
        void create_keepsExplicitDate() {
            Campaign campaign = new Campaign();
            campaign.setCurrentDate(GAME_DATE);
            givenCharacter(campaign);
            LocalDate explicit = LocalDate.of(2036, 1, 1);

            Money saved = moneyService.create(CHAR_ID, money(explicit));

            assertThat(saved.getOperationDate()).isEqualTo(explicit);
        }
    }

    private void givenCharacter(Campaign campaign) {
        Character character = new Character();
        character.setId(CHAR_ID);
        character.setCampaign(campaign);
        when(characterRepository.findById(CHAR_ID)).thenReturn(Optional.of(character));
        when(moneyRepository.save(any(Money.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Money money(LocalDate date) {
        Money money = new Money();
        money.setName("Гонорар");
        money.setAmount(500L);
        money.setOperationDate(date);
        return money;
    }
}
