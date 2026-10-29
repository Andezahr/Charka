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
    private final List<Integer> sidesAsked = new java.util.ArrayList<>();

    private CheckService checkService;

    @BeforeEach
    void setUp() {
        GameSystemRegistry registry = new GameSystemRegistry(List.of(new GenericSystem(), new Cp2020System(null)));
        checkService = new CheckService(registry, sides -> {
            sidesAsked.add(sides);
            return nextRoll;
        }, diceService, characterRepository, campaignRepository);
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
