package app.charka.check;

import app.charka.dice.DiceService;
import app.charka.exception.CharacterNotFoundException;
import app.charka.exception.NotFoundException;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.system.GameSystemRegistry;
import app.charka.system.GenericSystem;
import app.charka.system.cp2020.Cp2020System;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CheckService — проверки по правилам системы")
class CheckServiceTest {

    @Mock private DiceService diceService;
    @Mock private CharacterRepository characterRepository;
    @Mock private CampaignRepository campaignRepository;

    /** Последний «бросок» — для каждого кубика одно и то же значение */
    private int nextRoll;
    /** Известные броски по порядку; пока они есть, {@link #nextRoll} не используется */
    private final java.util.Deque<Integer> rolls = new java.util.ArrayDeque<>();
    private final List<Integer> sidesAsked = new java.util.ArrayList<>();

    private CheckService checkService;

    @BeforeEach
    void setUp() {
        GameSystemRegistry registry = new GameSystemRegistry(List.of(new GenericSystem(), new Cp2020System(null)));
        checkService = new CheckService(registry, sides -> {
            sidesAsked.add(sides);
            return rolls.isEmpty() ? nextRoll : rolls.poll();
        }, diceService, characterRepository, campaignRepository);
    }

    private static Character member(Long id, String name, Campaign campaign) {
        Character character = new Character();
        character.setId(id);
        character.setName(name);
        character.setCampaign(campaign);
        return character;
    }

    private static Campaign campaign(Long id, String system) {
        Campaign campaign = new Campaign();
        campaign.setId(id);
        campaign.setSystem(system);
        return campaign;
    }

    @Nested
    @DisplayName("Противопоставленная проверка")
    class OpposedTests {

        private final Campaign campaign = campaign(5L, Cp2020System.CODE);
        private final Character kolya = member(1L, "Колясик", campaign);
        private final Character oleg = member(2L, "Олежа", campaign);

        private OpposedCheckResult roll(int own, int other, int ownModifier, int otherModifier) {
            rolls.addAll(List.of(own, other));
            return checkService.checkOpposed(kolya, new CheckRequest("Пистолеты", ownModifier, 15),
                    oleg, null, new CheckRequest("Уклонение", otherModifier, 20));
        }

        @Test
        @DisplayName("больший итог побеждает, сложность запросов не учитывается")
        void higherWins() {
            OpposedCheckResult result = roll(7, 5, 12, 12);

            assertThat(result.winner()).isEqualTo(OpposedCheckResult.Winner.INITIATOR);
            assertThat(result.verdict()).isEqualTo("Победа: Колясик (19 против 17)");
            assertThat(result.initiator().result().difficulty()).isNull();
            assertThat(result.initiator().result().success()).isNull();
            assertThat(result.opponent().title()).isEqualTo("Олежа · Уклонение");
        }

        @Test
        @DisplayName("при равенстве побеждает защищающийся")
        void tieGoesToOpponent() {
            OpposedCheckResult result = roll(5, 7, 12, 10);

            assertThat(result.winner()).isEqualTo(OpposedCheckResult.Winner.OPPONENT);
            assertThat(result.verdict()).isEqualTo("Равенство 17 — победа защищающегося: Олежа");
        }

        @Test
        @DisplayName("фамбл проигрывает даже при большем итоге")
        void fumbleLoses() {
            rolls.addAll(List.of(1, 2));

            OpposedCheckResult result = checkService.checkOpposed(kolya, new CheckRequest("Пистолеты", 20, null),
                    oleg, null, new CheckRequest("Уклонение", 0, null));

            assertThat(result.winner()).isEqualTo(OpposedCheckResult.Winner.OPPONENT);
            assertThat(result.verdict()).isEqualTo("Критический провал: Колясик. Победа: Олежа");
        }

        @Test
        @DisplayName("оба фамбла — победителя нет")
        void bothFumble() {
            rolls.addAll(List.of(1, 1));

            OpposedCheckResult result = checkService.checkOpposed(kolya, new CheckRequest("Пистолеты", 5, null),
                    oleg, null, new CheckRequest("Уклонение", 5, null));

            assertThat(result.winner()).isEqualTo(OpposedCheckResult.Winner.NONE);
        }

        @Test
        @DisplayName("в логе запись у каждого участника: подпись с соперником, итог в расшифровке")
        void loggedForBoth() {
            roll(7, 5, 12, 12);

            verify(diceService).log(same(campaign), same(kolya), eq("d10!+12"),
                    eq("Бросок d10: 7; Модификатор +12 → 19; Победа: Колясик (19 против 17)"), eq(19), eq("Пистолеты vs Олежа"));
            verify(diceService).log(same(campaign), same(oleg), eq("d10!+12"),
                    eq("Бросок d10: 5; Модификатор +12 → 17; Победа: Колясик (19 против 17)"), eq(17), eq("Уклонение vs Колясик"));
        }

        @Test
        @DisplayName("противник без листа — запись мастера с его именем")
        void freeOpponent() {
            rolls.addAll(List.of(7, 5));

            OpposedCheckResult result = checkService.checkOpposed(kolya, new CheckRequest("Пистолеты", 12, null),
                    null, "Бандит", new CheckRequest("Бандит", 8, null));

            assertThat(result.opponent().title()).isEqualTo("Бандит");
            verify(diceService).log(same(campaign), isNull(), eq("d10!+8"), anyString(), eq(13), eq("Бандит vs Колясик"));
        }

        @Test
        @DisplayName("противник из другой кампании или сам персонаж — ошибка, в лог ничего")
        void wrongOpponent() {
            Character stranger = member(3L, "Чужой", campaign(6L, Cp2020System.CODE));

            assertThatThrownBy(() -> checkService.checkOpposed(kolya, new CheckRequest("А", 0, null),
                    stranger, null, new CheckRequest("Б", 0, null)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> checkService.checkOpposed(kolya, new CheckRequest("А", 0, null),
                    kolya, null, new CheckRequest("Б", 0, null)))
                    .isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(diceService);
        }
    }

    private Character characterIn(String system) {
        Campaign campaign = new Campaign();
        campaign.setSystem(system);
        Character character = new Character();
        character.setId(1L);
        character.setCampaign(campaign);
        when(characterRepository.findById(1L)).thenReturn(Optional.of(character));
        return character;
    }

    @Nested
    @DisplayName("Выбор правил")
    class RulesTests {

        @Test
        @DisplayName("персонаж CP2020 бросает d10")
        void cp2020() {
            characterIn(Cp2020System.CODE);
            nextRoll = 6;

            CheckResult result = checkService.checkForCharacter(1L, new CheckRequest(null, 4, 10));

            assertThat(sidesAsked).containsExactly(10);
            assertThat(result.notation()).startsWith("d10!");
            assertThat(result.success()).isTrue();
        }

        @Test
        @DisplayName("персонаж GENERIC бросает d20")
        void generic() {
            characterIn(GenericSystem.CODE);
            nextRoll = 12;

            CheckResult result = checkService.checkForCharacter(1L, new CheckRequest(null, 3, 15));

            assertThat(sidesAsked).containsExactly(20);
            assertThat(result.total()).isEqualTo(15);
            assertThat(result.success()).isTrue();
            assertThat(result.critical()).isFalse();
        }

        @Test
        @DisplayName("проверка мастера — по системе кампании")
        void campaignCheck() {
            Campaign campaign = new Campaign();
            campaign.setSystem(Cp2020System.CODE);
            when(campaignRepository.findById(5L)).thenReturn(Optional.of(campaign));
            nextRoll = 3;

            checkService.checkForCampaign(5L, new CheckRequest(null, 0, null));

            assertThat(sidesAsked).containsExactly(10);
            verify(diceService).log(same(campaign), isNull(), eq("d10!"), anyString(), eq(3), eq("Проверка"));
        }
    }

    @Nested
    @DisplayName("Лог")
    class LogTests {

        @Test
        @DisplayName("проверка пишется в лог: запись vs сложность, шаги, итог, подпись")
        void logged() {
            Character character = characterIn(Cp2020System.CODE);
            nextRoll = 7;

            checkService.checkForCharacter(1L, new CheckRequest("Взлом", 5, 15));

            verify(diceService).log(same(character.getCampaign()), same(character), eq("d10!+5 vs 15"),
                    eq("Бросок d10: 7; Модификатор +5 → 12; Сложность 15 → провал"), eq(12), eq("Взлом"));
        }

        @Test
        @DisplayName("неизвестный персонаж или кампания — 404-исключения, в лог ничего")
        void notFound() {
            when(characterRepository.findById(9L)).thenReturn(Optional.empty());
            when(campaignRepository.findById(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> checkService.checkForCharacter(9L, new CheckRequest(null, 0, null)))
                    .isInstanceOf(CharacterNotFoundException.class);
            assertThatThrownBy(() -> checkService.checkForCampaign(9L, new CheckRequest(null, 0, null)))
                    .isInstanceOf(NotFoundException.class);
            verifyNoInteractions(diceService);
        }
    }
}
