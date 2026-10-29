package app.charka.dice;

import app.charka.exception.CharacterNotFoundException;
import app.charka.exception.NotFoundException;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.RollLog;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.repository.RollLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DiceService")
class DiceServiceTest {

    private static final LocalDate GAME_DATE = LocalDate.of(2037, 10, 27);

    /** Выдаёт заранее заданные броски и запоминает, сколько граней просили */
    static class FixedDice implements DiceRoller {
        private final Deque<Integer> rolls = new ArrayDeque<>();
        private final Deque<Integer> sidesAsked = new ArrayDeque<>();

        FixedDice then(int... values) {
            for (int v : values) {
                rolls.add(v);
            }
            return this;
        }

        @Override
        public int roll(int sides) {
            sidesAsked.add(sides);
            return rolls.removeFirst();
        }
    }

    @Mock private RollLogRepository rollLogRepository;
    @Mock private CharacterRepository characterRepository;
    @Mock private CampaignRepository campaignRepository;

    private FixedDice dice;
    private DiceService diceService;

    @BeforeEach
    void setUp() {
        dice = new FixedDice();
        diceService = new DiceService(dice, rollLogRepository, characterRepository, campaignRepository);
    }

    @Test
    @DisplayName("бросает N кубиков нужных граней и складывает с модификатором")
    void rolls() {
        dice.then(4, 6, 1);

        DiceResult result = diceService.roll("3d6+2");

        assertThat(result.rolls()).containsExactly(4, 6, 1);
        assertThat(result.total()).isEqualTo(13);
        assertThat(dice.sidesAsked).containsExactly(6, 6, 6);
        verifyNoInteractions(rollLogRepository);
    }

    @Test
    @DisplayName("мусорный ввод — ошибка, кубики не бросаются")
    void garbage() {
        assertThatThrownBy(() -> diceService.roll("два кубика")).isInstanceOf(IllegalArgumentException.class);
        assertThat(dice.sidesAsked).isEmpty();
    }

    @Nested
    @DisplayName("Лог бросков")
    class LogTests {

        private Campaign campaign;

        @BeforeEach
        void setUp() {
            campaign = new Campaign();
            campaign.setId(7L);
            campaign.setCurrentDate(GAME_DATE);
        }

        private RollLog savedEntry() {
            ArgumentCaptor<RollLog> captor = ArgumentCaptor.forClass(RollLog.class);
            verify(rollLogRepository).save(captor.capture());
            return captor.getValue();
        }

        @Test
        @DisplayName("бросок персонажа пишется в лог его кампании с игровой датой")
        void characterRoll() {
            Character character = new Character();
            character.setId(1L);
            character.setCampaign(campaign);
            when(characterRepository.findById(1L)).thenReturn(Optional.of(character));
            dice.then(3, 5);

            DiceResult result = diceService.rollForCharacter(1L, " 2D6 + 3 ", "  Атака ");

            RollLog entry = savedEntry();
            assertThat(result.total()).isEqualTo(11);
            assertThat(entry.getCampaign()).isSameAs(campaign);
            assertThat(entry.getCharacter()).isSameAs(character);
            assertThat(entry.getExpression()).isEqualTo("2d6+3");
            assertThat(entry.getDetails()).isEqualTo("[3, 5] + 3 = 11");
            assertThat(entry.getTotal()).isEqualTo(11);
            assertThat(entry.getLabel()).isEqualTo("Атака");
            assertThat(entry.getGameDate()).isEqualTo(GAME_DATE);
            assertThat(entry.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("бросок мастера — без персонажа, пустая подпись не сохраняется")
        void campaignRoll() {
            when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaign));
            dice.then(20);

            diceService.rollForCampaign(7L, "d20", " ");

            RollLog entry = savedEntry();
            assertThat(entry.getCharacter()).isNull();
            assertThat(entry.getCampaign()).isSameAs(campaign);
            assertThat(entry.getLabel()).isNull();
        }

        @Test
        @DisplayName("ошибка в выражении — в лог ничего не пишется")
        void garbageNotLogged() {
            when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaign));

            assertThatThrownBy(() -> diceService.rollForCampaign(7L, "2d", null))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(rollLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("неизвестный персонаж или кампания — 404-исключения")
        void notFound() {
            when(characterRepository.findById(99L)).thenReturn(Optional.empty());
            when(campaignRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> diceService.rollForCharacter(99L, "d6", null))
                    .isInstanceOf(CharacterNotFoundException.class);
            assertThatThrownBy(() -> diceService.rollForCampaign(99L, "d6", null))
                    .isInstanceOf(NotFoundException.class);
            verify(rollLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("последние броски кампании берутся с лимитом")
        void recent() {
            RollLog entry = new RollLog();
            when(rollLogRepository.findByCampaignIdOrderByCreatedAtDescIdDesc(eq(7L), any()))
                    .thenReturn(List.of(entry));

            assertThat(diceService.recentForCampaign(7L, 20)).containsExactly(entry);
        }
    }
}
