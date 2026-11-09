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
    @Autowired private app.charka.map.BattleMapService battleMapService;
    @Autowired private app.charka.map.BattleFieldService battleFieldService;

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

        assertThat(html).contains("me-panel", "Ходит Бандит · до вас ещё 2 ход.", "Действовать и двигать фишку — на своём ходу", "data-tab-panel=\"queue\"",
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

    @Test
    @DisplayName("M-16, M-18: враг за стеной у игрока в очереди — «?», в списках целей его нет; мастеру — «👁 нет»")
    void hiddenEnemy() throws Exception {
        app.charka.map.MapDoc doc = app.charka.map.MapDoc.empty(12, 8, 2);
        for (int y = 0; y < 8; y++) {
            doc.walls().put(app.charka.map.MapDoc.left(6, y), "cover_brick");
        }
        Long mapId = battleMapService.save(campaign.getId(), "Стена", doc).getId();
        battleFieldService.attach(campaign.getId(), mapId);
        Long banditToken = encounterService.view(campaign.getId()).orElseThrow().rows().stream()
                .filter(row -> row.character().getId().equals(bandit.getId())).findFirst().orElseThrow().participant().getId();
        Long mineToken = encounterService.view(campaign.getId()).orElseThrow().rows().stream()
                .filter(row -> row.character().getId().equals(mine.getId())).findFirst().orElseThrow().participant().getId();
        Long boosterToken = encounterService.view(campaign.getId()).orElseThrow().rows().stream()
                .filter(row -> row.character().getId().equals(booster.getId())).findFirst().orElseThrow().participant().getId();
        battleFieldService.move(campaign.getId(), mineToken, 1, 1, true);
        battleFieldService.move(campaign.getId(), boosterToken, 2, 1, true);
        battleFieldService.move(campaign.getId(), banditToken, 10, 1, true);

        String html = page("kolya");
        assertThat(html).contains("<span class=\"carousel-name\">?</span>", "кто-то вне видимости")
                .doesNotContain(">Бандит<", "Ходит Бандит");
        assertThat(html).contains(">Бустер<");
        assertThat(page("master")).contains(">Бандит<", "👁 нет");
    }
}
