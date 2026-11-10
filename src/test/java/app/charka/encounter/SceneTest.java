package app.charka.encounter;

import app.charka.model.BattleMap;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.EncounterParticipant;
import app.charka.model.Scene;
import app.charka.map.BattleMapService;
import app.charka.repository.CampaignRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Transactional
@DisplayName("Заготовка боя — сцена (A-06)")
class SceneTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterService characterService;
    @Autowired private BattleMapService battleMapService;
    @Autowired private SceneService sceneService;
    @Autowired private EncounterService encounterService;
    @Autowired private EntityManager entityManager;

    private Campaign campaign;
    private Character player;
    private Character boss;
    private BattleMap map;

    @BeforeEach
    void setUp() {
        Campaign created = new Campaign();
        created.setName("Тест");
        created.setSystem("CP2020");
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        campaign = campaignRepository.save(created);
        player = member("Колясик", false);
        boss = member("Босс", true);
        map = battleMapService.create(campaign.getId(), "Бар", 20, 12);
        clear();
    }

    private Character member(String name, boolean npc) {
        Character character = new Character();
        character.setName(name);
        character.setNpc(npc);
        return characterService.createInCampaign(campaign.getId(), character);
    }

    private void clear() {
        entityManager.flush();
        entityManager.clear();
    }

    private Scene preparedScene() {
        Scene scene = sceneService.create(campaign.getId(), "Засада у бара", map.getId());
        sceneService.addNpc(campaign.getId(), scene.getId(), "t:bandit", null, 4, false, false);
        sceneService.addNpc(campaign.getId(), scene.getId(), "c:" + boss.getId(), null, 1, false, true);
        clear();
        return scene;
    }

    @Test
    @DisplayName("Бой с картой и 5 NPC — одним нажатием: NPC созданы, фишки на карте, засада ждёт; сцена осталась")
    void startInOneClick() throws Exception {
        Scene scene = preparedScene();

        String page = mockMvc.perform(get("/campaign/" + campaign.getId() + "/encounter"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Засада у бара", "NPC: 5 · все игроки", "/scenes/" + scene.getId() + "/start");

        mockMvc.perform(post("/campaign/" + campaign.getId() + "/scenes/" + scene.getId() + "/start"))
                .andExpect(redirectedUrl("/campaign/" + campaign.getId() + "/encounter"));
        clear();

        EncounterView view = encounterService.view(campaign.getId()).orElseThrow();
        List<EncounterParticipant> participants = view.rows().stream().map(EncounterView.Row::participant).toList();
        assertThat(view.rows()).hasSize(6);
        assertThat(view.rows()).extracting(row -> row.character().getName())
                .contains("Колясик", "Босс").filteredOn(name -> name.startsWith("Бандит")).hasSize(4);
        assertThat(participants).allSatisfy(participant -> assertThat(participant.getPosX()).isNotNull());
        assertThat(view.encounter().getBattleMapId()).isEqualTo(map.getId());
        assertThat(view.rows()).filteredOn(row -> row.character().getId().equals(boss.getId()))
                .singleElement().satisfies(row -> assertThat(row.participant().isWaiting()).isTrue());
        assertThat(view.current().getId()).isNotEqualTo(boss.getId());
        assertThat(sceneService.list(campaign.getId())).singleElement().satisfies(kept -> assertThat(kept.getNpcs()).hasSize(2));
    }

    @Test
    @DisplayName("Пока идёт бой, сцену не начать; после боя — снова, с новыми NPC")
    void replay() {
        Scene scene = preparedScene();
        sceneService.start(campaign.getId(), scene.getId());
        clear();
        assertThatThrownBy(() -> sceneService.start(campaign.getId(), scene.getId())).hasMessageContaining("Бой уже идёт");

        encounterService.end(campaign.getId());
        clear();
        sceneService.start(campaign.getId(), scene.getId());
        clear();

        assertThat(campaignRepository.findById(campaign.getId()).orElseThrow().getCharacters())
                .filteredOn(character -> character.getName().startsWith("Бандит")).hasSize(8);
    }

    @Test
    @DisplayName("Страница сцен: строка NPC, засада переключается одним нажатием, чужой шаблон — отказ")
    void editing() throws Exception {
        Scene scene = preparedScene();
        Long banditRow = sceneService.get(campaign.getId(), scene.getId()).getNpcs().get(0).getId();

        mockMvc.perform(post("/campaign/" + campaign.getId() + "/scenes/" + scene.getId() + "/npcs/" + banditRow + "/ambush"));
        clear();
        assertThat(sceneService.get(campaign.getId(), scene.getId()).getNpcs().get(0).isAmbush()).isTrue();

        String page = mockMvc.perform(get("/campaign/" + campaign.getId() + "/scenes"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Бандит × 4", "Босс", "В засаде", "✎ Зоны старта на карте");
        assertThatThrownBy(() -> sceneService.addNpc(campaign.getId(), scene.getId(), "t:dragon", null, 1, false, false))
                .hasMessageContaining("Нет такого шаблона");
    }
}
