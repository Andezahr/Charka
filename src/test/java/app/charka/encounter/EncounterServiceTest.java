package app.charka.encounter;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Encounter;
import app.charka.model.EncounterParticipant;
import app.charka.model.counters.strategy.health.DamageRequest;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.npc.NpcService;
import app.charka.repository.CampaignRepository;
import app.charka.service.CharacterService;
import app.charka.service.counter.HealthCounterService;
import app.charka.skill.SkillService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Боевой трекер")
class EncounterServiceTest {

    @Autowired private EncounterService encounterService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterService characterService;
    @Autowired private HealthCounterService healthCounterService;
    @Autowired private SkillService skillService;
    @Autowired private NpcService npcService;
    @Autowired private EntityManager entityManager;

    @MockitoBean(answers = Answers.CALLS_REAL_METHODS) private DiceRoller dice;

    private Campaign campaign;
    private Character anna;
    private Character boris;
    private Character vera;

    @BeforeEach
    void setUp() {
        campaign = campaign("CP2020");
        anna = member(campaign, "Анна");
        boris = member(campaign, "Борис");
        vera = member(campaign, "Вера");
        when(dice.roll(anyInt())).thenReturn(5);
    }

    private Campaign campaign(String system) {
        Campaign created = new Campaign();
        created.setName("Тест");
        created.setSystem(system);
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        return campaignRepository.save(created);
    }

    private Character member(Campaign owner, String name) {
        Character character = new Character();
        character.setName(name);
        return characterService.createInCampaign(owner.getId(), character);
    }

    private void start(Character... fighters) {
        reload();
        encounterService.start(campaign.getId(), List.of(fighters).stream().map(Character::getId).toList());
    }

    /** Читаем из базы: кампания из этой сессии не знает своих персонажей, а заодно видно, что всё сохранилось */
    private void reload() {
        entityManager.flush();
        entityManager.clear();
    }

    private EncounterView view() {
        reload();
        return encounterService.view(campaign.getId()).orElseThrow();
    }

    private List<String> order() {
        return view().rows().stream().map(row -> row.character().getName()).toList();
    }

    private String current() {
        Character current = view().current();
        return current == null ? null : current.getName();
    }

    private void setInitiative(Character character, int initiative) {
        encounterService.setInitiative(campaign.getId(), participant(character).getId(), initiative);
    }

    private EncounterParticipant participant(Character character) {
        return view().rows().stream().filter(row -> row.character().getId().equals(character.getId()))
                .findFirst().orElseThrow().participant();
    }

    private void kill(Character character) {
        healthCounterService.applyDamage(character.getId(), DamageRequest.builder().amount(100).location("HEAD").build());
    }

    @Nested
    @DisplayName("Начало боя")
    class Start {

        @Test
        @DisplayName("инициатива бросается всем: REF + d10 и порядок от большей")
        void rollsForEveryone() {
            when(dice.roll(10)).thenReturn(3, 9, 6);

            start(anna, boris, vera);

            assertThat(view().rows()).extracting(row -> row.participant().getInitiative()).isSortedAccordingTo(java.util.Comparator.reverseOrder());
            assertThat(view().rows()).allSatisfy(row -> assertThat(row.participant().getInitiativeDetails()).startsWith("REF ").contains("+ d10 "));
            assertThat(view().encounter().getRound()).isEqualTo(1);
            assertThat(current()).isEqualTo(order().getFirst());
        }

        @Test
        @DisplayName("бой один: второй не начинается")
        void onlyOne() {
            start(anna);

            assertThatThrownBy(() -> encounterService.start(campaign.getId(), List.of(boris.getId())))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("уже идёт");
        }

        @Test
        @DisplayName("без участников и с чужим персонажем — ошибка")
        void wrongParticipants() {
            assertThatThrownBy(() -> encounterService.start(campaign.getId(), List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            Character stranger = member(campaign("CP2020"), "Чужой");
            assertThatThrownBy(() -> encounterService.start(campaign.getId(), List.of(stranger.getId())))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Чужой");
        }

        @Test
        @DisplayName("при равной инициативе игрок раньше NPC (HOMEBREW)")
        void playerBeforeNpcOnTie() {
            Character npc = npcService.create(campaign.getId(), null, "Бандит", 1, false).getFirst();

            start(npc, anna);
            setInitiative(npc, 10);
            setInitiative(anna, 10);

            assertThat(order()).containsExactly("Анна", npc.getName());
        }
    }

    @Nested
    @DisplayName("Инициатива CP2020")
    class Cp2020 {

        @Test
        @DisplayName("Боевое чутьё прибавляется только Соло")
        void combatSenseOnlyForSolo() {
            skillService.setRoles(anna.getId(), List.of("solo"));
            skillService.updateLevels(anna.getId(), Map.of("combat_sense", 4), Map.of());

            start(anna, boris);

            assertThat(participant(anna).getInitiativeDetails()).contains("+ d10 5").contains(" 4 = ");
            assertThat(participant(boris).getInitiativeDetails()).doesNotContain(" 4 = ");
            assertThat(participant(anna).getInitiative()).isEqualTo(participant(boris).getInitiative() + 4);
        }

        @Test
        @DisplayName("десятка не даёт дополнительного кубика: это не проверка")
        void tenDoesNotExplode() {
            when(dice.roll(10)).thenReturn(10);
            start(anna);
            int withTen = participant(anna).getInitiative();
            when(dice.roll(10)).thenReturn(1);
            encounterService.next(campaign.getId());

            assertThat(withTen - participant(anna).getInitiative()).isEqualTo(9);
        }

        @Test
        @DisplayName("новый раунд: инициатива бросается заново")
        void rerollsEachRound() {
            when(dice.roll(10)).thenReturn(5);
            start(anna);
            int first = participant(anna).getInitiative();
            when(dice.roll(10)).thenReturn(9);

            encounterService.next(campaign.getId());

            assertThat(view().encounter().getRound()).isEqualTo(2);
            assertThat(participant(anna).getInitiative()).isEqualTo(first + 4);
        }
    }

    @Nested
    @DisplayName("Очередь")
    class Turns {

        @BeforeEach
        void fight() {
            start(anna, boris, vera);
            setInitiative(anna, 30);
            setInitiative(boris, 20);
            setInitiative(vera, 10);
        }

        @Test
        @DisplayName("следующий ход идёт по очереди, после последнего — новый раунд")
        void walksTheOrder() {
            assertThat(current()).isEqualTo("Анна");
            assertThat(view().lastInRound()).isFalse();
            encounterService.next(campaign.getId());
            assertThat(current()).isEqualTo("Борис");
            encounterService.next(campaign.getId());
            assertThat(current()).isEqualTo("Вера");
            assertThat(view().lastInRound()).isTrue();

            encounterService.next(campaign.getId());

            assertThat(view().encounter().getRound()).isEqualTo(2);
            assertThat(view().rows()).noneMatch(row -> row.participant().isDone());
        }

        @Test
        @DisplayName("мёртвый пропускается и не предлагается целью")
        void deadIsSkipped() {
            kill(boris);

            encounterService.next(campaign.getId());

            assertThat(current()).isEqualTo("Вера");
            assertThat(view().rows().getFirst().targets()).extracting(Character::getName).containsExactly("Вера");
        }

        @Test
        @DisplayName("текущего убили — ход сразу у следующего")
        void currentKilled() {
            kill(anna);

            assertThat(current()).isEqualTo("Борис");
        }

        @Test
        @DisplayName("ждавший пропускает ход, вступает раньше текущего, затем ход возвращается")
        void waitAndJoin() {
            encounterService.waitTurn(campaign.getId(), participant(anna).getId());
            assertThat(current()).isEqualTo("Борис");
            encounterService.next(campaign.getId());
            assertThat(current()).isEqualTo("Вера");

            encounterService.join(campaign.getId(), participant(anna).getId());

            assertThat(current()).isEqualTo("Анна");
            encounterService.next(campaign.getId());
            assertThat(current()).isEqualTo("Вера");
        }

        @Test
        @DisplayName("ждавший, так и не вступивший, ходит последним в раунде")
        void waiterGoesLast() {
            encounterService.waitTurn(campaign.getId(), participant(anna).getId());
            encounterService.next(campaign.getId());
            encounterService.next(campaign.getId());

            assertThat(current()).isEqualTo("Анна");
            assertThat(view().encounter().getRound()).isEqualTo(1);
        }

        @Test
        @DisplayName("вступивший с высокой инициативой не отнимает ход у текущего")
        void addedAfterCurrent() {
            encounterService.next(campaign.getId());
            Character late = member(campaign, "Гриша");
            when(dice.roll(10)).thenReturn(10);

            encounterService.add(campaign.getId(), late.getId());

            assertThat(current()).isEqualTo("Борис");
            encounterService.next(campaign.getId());
            assertThat(current()).isEqualTo("Гриша");
        }

        @Test
        @DisplayName("второй раз в бой не вводится")
        void addedTwice() {
            assertThatThrownBy(() -> encounterService.add(campaign.getId(), anna.getId()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("уже в бою");
        }

        @Test
        @DisplayName("убранный из боя пропадает из очереди")
        void removed() {
            encounterService.remove(campaign.getId(), participant(anna).getId());

            assertThat(order()).containsExactly("Борис", "Вера");
        }

        @Test
        @DisplayName("урон участнику доходит до его здоровья")
        void damage() {
            var event = encounterService.damage(campaign.getId(), participant(boris).getId(), 3, "TORSO", null);

            assertThat(event.getSummary()).isNotBlank();
            assertThat(participant(boris)).isNotNull();
            assertThat(view().rows().get(1).card().lines()).anyMatch(line -> line.startsWith("Урон "));
        }

        @Test
        @DisplayName("конец боя убирает его из кампании")
        void end() {
            encounterService.end(campaign.getId());
            reload();

            assertThat(encounterService.activeRound(campaign.getId())).isNull();
            assertThat(encounterService.campaignInFight(anna.getId())).isNull();
        }
    }

    @Test
    @DisplayName("удалённый NPC уходит из боя вместе с листом")
    void deletedNpcLeavesFight() {
        Character npc = npcService.create(campaign.getId(), null, "Бандит", 1, false).getFirst();
        start(anna, npc);
        Long participantId = participant(npc).getId();
        reload();

        npcService.delete(npc.getId());
        reload();

        assertThat(entityManager.find(EncounterParticipant.class, participantId)).isNull();
        assertThat(order()).containsExactly("Анна");
    }

    @Test
    @DisplayName("система без своих правил: d20 один раз за бой, урон применяет мастер")
    void genericSystem() {
        campaign = campaign("GENERIC");
        Character hero = member(campaign, "Герой");
        when(dice.roll(20)).thenReturn(17);
        start(hero);

        assertThat(participant(hero).getInitiative()).isEqualTo(17);
        when(dice.roll(20)).thenReturn(2);
        encounterService.next(campaign.getId());

        Encounter encounter = view().encounter();
        assertThat(encounter.getRound()).isEqualTo(2);
        assertThat(participant(hero).getInitiative()).isEqualTo(17);
        assertThat(view().rows().getFirst().card().healthCounterId()).isNull();
    }
}
