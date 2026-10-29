package app.charka.service;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Wound;
import app.charka.repository.CharacterRepository;
import app.charka.repository.WoundRepository;
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
@DisplayName("WoundService")
class WoundServiceTest {

    private static final long CHAR_ID = 1L;
    private static final LocalDate GAME_DATE = LocalDate.of(2037, 10, 27);

    @Mock private WoundRepository woundRepository;
    @Mock private CharacterRepository characterRepository;
    @InjectMocks private WoundService woundService;

    @Nested
    @DisplayName("Дата раны при создании")
    class CreateDateTests {

        @Test
        @DisplayName("без даты — ставится игровая дата кампании")
        void create_usesCampaignCurrentDate() {
            Campaign campaign = new Campaign();
            campaign.setCurrentDate(GAME_DATE);
            givenCharacter(campaign);

            Wound saved = woundService.create(CHAR_ID, wound(null));

            assertThat(saved.getDate()).isEqualTo(GAME_DATE);
        }

        @Test
        @DisplayName("персонаж без кампании — реальная дата")
        void create_withoutCampaign_usesToday() {
            givenCharacter(null);

            Wound saved = woundService.create(CHAR_ID, wound(null));

            assertThat(saved.getDate()).isEqualTo(LocalDate.now());
        }

        @Test
        @DisplayName("явно заданная дата не перезаписывается")
        void create_keepsExplicitDate() {
            Campaign campaign = new Campaign();
            campaign.setCurrentDate(GAME_DATE);
            givenCharacter(campaign);
            LocalDate explicit = LocalDate.of(2036, 1, 1);

            Wound saved = woundService.create(CHAR_ID, wound(explicit));

            assertThat(saved.getDate()).isEqualTo(explicit);
        }
    }

    private void givenCharacter(Campaign campaign) {
        Character character = new Character();
        character.setId(CHAR_ID);
        character.setCampaign(campaign);
        when(characterRepository.findById(CHAR_ID)).thenReturn(Optional.of(character));
        when(woundRepository.save(any(Wound.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Wound wound(LocalDate date) {
        Wound wound = new Wound();
        wound.setName("Порез");
        wound.setDate(date);
        return wound;
    }
}
