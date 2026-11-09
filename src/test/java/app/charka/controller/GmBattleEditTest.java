package app.charka.controller;

import app.charka.encounter.EncounterService;
import app.charka.encounter.EncounterView;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.model.JournalEntry;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.health.DamageRequest;
import app.charka.repository.AppUserRepository;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.repository.JournalEntryRepository;
import app.charka.security.UserService;
import app.charka.service.CharacterService;
import app.charka.service.ItemService;
import app.charka.service.counter.HealthCounterService;
import app.charka.system.kosmozeki.KzAbilities;
import app.charka.system.kosmozeki.KzBody;
import app.charka.system.kosmozeki.KzBodyStrategy;
import app.charka.system.kosmozeki.KzSystem;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GM-01: мастер с карты боя лечит, бьёт и правит число действий участника; BF-01: лечение «в нулях» и атака в том же раунде.
 */
@SpringBootTest(properties = "charka.security.enabled=true")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Transactional
@DisplayName("Правка мастера в бою (GM-01, BF-01)")
class GmBattleEditTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private AppUserRepository userRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private CharacterService characterService;
    @Autowired private ItemService itemService;
    @Autowired private EncounterService encounterService;
    @Autowired private HealthCounterService healthCounterService;
    @Autowired private JournalEntryRepository journalRepository;
    @Autowired private EntityManager entityManager;

    private Campaign campaign;
    private Character zek;
    private Character guard;
    private String base;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        Campaign created = new Campaign();
        created.setName("Каркосса");
        created.setSystem(KzSystem.CODE);
        created.setStartDate(LocalDate.of(2026, 1, 1));
        created.setCurrentDate(LocalDate.of(2026, 1, 1));
        campaign = campaignRepository.save(created);
        zek = member("Зэк", false);
        guard = member("Охранник", true);
        userService.register("master", "secret1");
        zek.setPlayer(userService.register("kolya", "secret2"));
        characterRepository.save(zek);
        base = "/campaign/" + campaign.getId() + "/encounter";
        encounterService.start(campaign.getId(), List.of(zek.getId(), guard.getId()));
        encounterService.setInitiative(campaign.getId(), row(guard).participant().getId(), 30);
        encounterService.setInitiative(campaign.getId(), row(zek).participant().getId(), 10);
        flush();
    }

    private Character member(String name, boolean npc) {
        Character character = new Character();
        character.setName(name);
        character.setNpc(npc);
        return characterService.createInCampaign(campaign.getId(), character);
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }

    private EncounterView.Row row(Character character) {
        return encounterService.view(campaign.getId()).orElseThrow().rows().stream()
                .filter(row -> row.character().getId().equals(character.getId())).findFirst().orElseThrow();
    }

    private String participant(Character character) {
        return base + "/participants/" + row(character).participant().getId();
    }

    private int hp(Character character, String zone) {
        Counter body = KzAbilities.bodyCounter(characterRepository.findById(character.getId()).orElseThrow()).orElseThrow();
        return KzBodyStrategy.hp(body, KzBodyStrategy.body(body).zone(zone).orElseThrow());
    }

    private void damage(Character character, String zone, int amount) {
        healthCounterService.applyDamage(character.getId(), DamageRequest.builder().amount(amount).location(zone).build());
        flush();
    }

    private List<JournalEntry> journal() {
        return journalRepository.findByCampaignIdOrderByCreatedAtDescIdDesc(campaign.getId(), PageRequest.of(0, 10));
    }

    @Nested
    @DisplayName("Лечение мастером")
    class Heal {

        @Test
        @DisplayName("Зона Космозэков: +40 в правую ногу, запись «Лечение» в журнале боя, плашка отмены")
        void zone() throws Exception {
            damage(zek, KzBody.RIGHT_LEG, 60);

            mockMvc.perform(post(participant(zek) + "/heal").param("points", "40").param("location", KzBody.RIGHT_LEG)
                            .with(user("master")).with(csrf()))
                    .andExpect(redirectedUrl(base + "#combatant-" + zek.getId()))
                    .andExpect(flash().attributeExists("combatResult", "undoOffer"));
            flush();

            assertThat(hp(zek, KzBody.RIGHT_LEG)).isEqualTo(80);
            assertThat(hp(zek, KzBody.HEAD)).isEqualTo(100);
            assertThat(journal().getFirst().getTitle()).isEqualTo("Лечение: Зэк");
        }

        @Test
        @DisplayName("Без зоны — каждой зоне; из нулей персонаж снова в очереди")
        void allZonesWakesUp() throws Exception {
            damage(zek, KzBody.RIGHT_LEG, 100);
            assertThat(row(zek).card().out()).isTrue();

            mockMvc.perform(post(participant(zek) + "/heal").param("points", "30").with(user("master")).with(csrf()));
            flush();

            assertThat(hp(zek, KzBody.RIGHT_LEG)).isEqualTo(30);
            assertThat(row(zek).card().out()).isFalse();
        }

        @Test
        @DisplayName("Ноль очков — отказ словами, без 500")
        void zeroRefused() throws Exception {
            mockMvc.perform(post(participant(zek) + "/heal").param("points", "0").with(user("master")).with(csrf()))
                    .andExpect(flash().attribute("encounterError", "Сколько вылечить — от 1"));
        }
    }

    @Nested
    @DisplayName("Действия раунда")
    class Actions {

        @Test
        @DisplayName("Потратил действие — мастер даёт ещё одно, потом забирает; в журнале")
        void giveAndTake() throws Exception {
            String url = participant(guard) + "/actions";
            encounterService.adjustActions(campaign.getId(), row(guard).participant().getId(), 1);
            flush();
            assertThat(row(guard).canAct()).isFalse();

            mockMvc.perform(post(url).param("delta", "-1").with(user("master")).with(csrf()))
                    .andExpect(redirectedUrl(base + "#combatant-" + guard.getId()));
            flush();
            assertThat(row(guard).canAct()).isTrue();
            assertThat(journal().getFirst().getTitle()).isEqualTo("Мастер дал действие: Охранник");

            mockMvc.perform(post(url).param("delta", "1").with(user("master")).with(csrf()));
            flush();
            assertThat(row(guard).canAct()).isFalse();
            assertThat(journal().getFirst().getOutcome()).isEqualTo("Действий в раунде не осталось");
        }

        @Test
        @DisplayName("Не больше пяти сверх: шестое — отказ")
        void limit() throws Exception {
            Long pid = row(guard).participant().getId();
            for (int i = 0; i < 5; i++) {
                encounterService.adjustActions(campaign.getId(), pid, -1);
            }
            flush();

            mockMvc.perform(post(participant(guard) + "/actions").param("delta", "-1").with(user("master")).with(csrf()))
                    .andExpect(flash().attribute("encounterError", "Больше действий в этом раунде дать нельзя"));
        }
    }

    @Nested
    @DisplayName("Права и страница")
    class Access {

        @Test
        @DisplayName("Игрок не лечит и не правит действия даже своего персонажа — 404")
        void playerRefused() throws Exception {
            mockMvc.perform(post(participant(zek) + "/heal").param("points", "50").with(user("kolya")).with(csrf()))
                    .andExpect(status().isNotFound());
            mockMvc.perform(post(participant(zek) + "/actions").param("delta", "-1").with(user("kolya")).with(csrf()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("У мастера в правке участника — «Вылечить» с выбором зоны и ＋/− действие; у игрока нет")
        void forms() throws Exception {
            String gm = mockMvc.perform(get(base).with(user("master"))).andReturn().getResponse().getContentAsString();
            String player = mockMvc.perform(get(base).with(user("kolya"))).andReturn().getResponse().getContentAsString();

            assertThat(gm).contains("encounter-heal", "Все зоны", "Правая нога", "encounter-actions-adjust", "＋ Действие", "− Действие",
                    "Правка: урон, лечение, действия, инициатива");
            assertThat(player).doesNotContain("encounter-heal", "encounter-actions-adjust");
        }
    }

    @Nested
    @DisplayName("BF-01: в нулях — лечение — атака в том же раунде")
    class HealThenAttack {

        @Test
        @DisplayName("Лечение «Жидкой кожей» тратит действие; атака из боя — отказ словами и возврат в бой, не 500")
        void attackAfterHeal() throws Exception {
            Long inventory = characterRepository.findById(zek.getId()).orElseThrow().getInventories().getFirst().getId();
            Item skin = itemService.addFromCatalog(inventory, "kz-skin", 3);
            Item pistol = itemService.addFromCatalog(inventory, "kz-pistol", 1);
            damage(zek, KzBody.RIGHT_LEG, 100);
            encounterService.next(campaign.getId());
            flush();

            mockMvc.perform(post("/character/" + zek.getId() + "/inventories/" + inventory + "/items/" + skin.getId() + "/action/HEAL_ALL")
                            .param("returnCampaign", String.valueOf(campaign.getId())).with(user("kolya")).with(csrf()))
                    .andExpect(status().is3xxRedirection());
            flush();
            assertThat(row(zek).card().out()).isFalse();
            assertThat(row(zek).canAct()).isFalse();

            mockMvc.perform(post("/character/" + zek.getId() + "/items/" + pistol.getId() + "/attack")
                            .param("target", String.valueOf(guard.getId())).param("mode", "FIRE").param("rounds", "1")
                            .param("returnCampaign", String.valueOf(campaign.getId())).with(user("kolya")).with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attribute("actionError", app.charka.encounter.RoundActions.NO_ACTIONS_LEFT));
            flush();

            String page = mockMvc.perform(get(base).with(user("kolya"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(page).contains("Действие в этом раунде сделано");
        }
    }
}
