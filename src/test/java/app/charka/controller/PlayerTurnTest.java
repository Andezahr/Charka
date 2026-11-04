package app.charka.controller;

import app.charka.encounter.EncounterService;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.EncounterParticipant;
import app.charka.repository.AppUserRepository;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.security.UserService;
import app.charka.service.CharacterService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "charka.security.enabled=true")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Transactional
@DisplayName("Режим «мой ход» для игрока (A-04)")
class PlayerTurnTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private AppUserRepository userRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private CharacterService characterService;
    @Autowired private EncounterService encounterService;
    @Autowired private EntityManager entityManager;

    private Campaign campaign;
    private Character mine;
    private Character bandit;
    private Character booster;
    private String base;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        Campaign created = new Campaign();
        created.setName("Ночной город");
        created.setSystem("CP2020");
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        campaign = campaignRepository.save(created);
        mine = member("Колясик", false);
        bandit = member("Бандит", true);
        booster = member("Бустер", true);
        userService.register("master", "secret1");
        mine.setPlayer(userService.register("kolya", "secret2"));
        characterRepository.save(mine);
        base = "/campaign/" + campaign.getId() + "/encounter";
        encounterService.start(campaign.getId(), List.of(mine.getId(), bandit.getId(), booster.getId()));
        // порядок: Бандит, Бустер, Колясик
        initiative(bandit, 30);
        initiative(booster, 20);
        initiative(mine, 10);
        entityManager.flush();
        entityManager.clear();
    }

    private Character member(String name, boolean npc) {
        Character character = new Character();
        character.setName(name);
        character.setNpc(npc);
        return characterService.createInCampaign(campaign.getId(), character);
    }

    private void initiative(Character character, int value) {
        EncounterParticipant participant = encounterService.view(campaign.getId()).orElseThrow().rows().stream()
                .filter(row -> row.character().getId().equals(character.getId())).findFirst().orElseThrow().participant();
        encounterService.setInitiative(campaign.getId(), participant.getId(), value);
    }

    private String page(String who) throws Exception {
        entityManager.flush();
        entityManager.clear();
        return mockMvc.perform(get(base).with(user(who))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("Не свой ход: портрет «Я» — «Ходит Бандит · до вас ещё 2», очередь во вкладке, «Следующий ход» не у игрока")
    void waiting() throws Exception {
        String html = page("kolya");

        assertThat(html).contains("me-panel", "Ходит Бандит · до вас ещё 2 ход.", "Действия вне очереди", "data-tab-panel=\"queue\"",
                "data-turn-watch=\"" + base + "/turn\"", "data-mine=\"" + mine.getId() + "\"")
                .doesNotContain("Следующий ход →", "Закончить ход →");
        assertThat(page("master")).contains("Следующий ход →").doesNotContain("me-panel");
    }

    @Test
    @DisplayName("Чужой ход игрок не передаст; на своём — «Ваш ход» и «Закончить ход», ход уходит дальше")
    void endOwnTurn() throws Exception {
        mockMvc.perform(post(base + "/next").with(user("kolya")).with(csrf()))
                .andExpect(flash().attribute("encounterError", "Сейчас ход не ваш: передать его может тот, кто ходит, или мастер"));

        mockMvc.perform(post(base + "/next").with(user("master")).with(csrf()));
        assertThat(page("kolya")).contains("Ходит Бустер · вы следующий");
        mockMvc.perform(post(base + "/next").with(user("master")).with(csrf()));

        assertThat(page("kolya")).contains("▶ Ваш ход", "Закончить ход →", "is-mine");
        String turn = mockMvc.perform(get(base + "/turn").with(user("kolya")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(turn).contains("\"current\":" + mine.getId(), "\"name\":\"Колясик\"");

        mockMvc.perform(post(base + "/next").with(user("kolya")).with(csrf()))
                .andExpect(flash().attribute("encounterError", org.hamcrest.Matchers.nullValue()));
        entityManager.flush();
        entityManager.clear();
        // Колясик ходил последним: раунд кончился, инициатива бросается заново (с. 97)
        assertThat(encounterService.view(campaign.getId()).orElseThrow().encounter().getRound()).isEqualTo(2);
    }
}
