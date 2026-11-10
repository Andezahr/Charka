package app.charka.dice;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.ChatMessage;
import app.charka.model.RollLog;
import app.charka.repository.AppUserRepository;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.repository.ChatMessageRepository;
import app.charka.repository.RollLogRepository;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "charka.security.enabled=true")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Transactional
@DisplayName("Скрытый бросок мастера (A-09)")
class HiddenRollTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private AppUserRepository userRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private CharacterService characterService;
    @Autowired private RollLogRepository rollLogRepository;
    @Autowired private ChatMessageRepository chatRepository;
    @Autowired private EntityManager entityManager;

    private Campaign campaign;
    private Character kolya;
    private Character bandit;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        Campaign created = new Campaign();
        created.setName("Ночной город");
        created.setSystem("CP2020");
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        campaign = campaignRepository.save(created);
        kolya = member("Колясик", false);
        bandit = member("Бандит", true);
        userService.register("master", "secret1");
        kolya.setPlayer(userService.register("kolya", "secret2"));
        characterRepository.save(kolya);
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

    private MvcResult chat(String who) throws Exception {
        clear();
        return mockMvc.perform(get("/campaign/" + campaign.getId() + "/chat").with(user(who)))
                .andExpect(status().isOk()).andReturn();
    }

    @Test
    @DisplayName("Скрытый бросок игрок не видит ни в ленте, ни по ETag опроса; мастер видит приглушённым и показывает всем")
    void hiddenRoll() throws Exception {
        String etagBefore = chat("kolya").getResponse().getHeader("ETag");

        mockMvc.perform(post("/campaign/" + campaign.getId() + "/roll").param("expression", "1d10").param("label", "Засада врага")
                .param("hidden", "true").with(user("master")).with(csrf()));

        MvcResult player = chat("kolya");
        assertThat(player.getResponse().getContentAsString()).doesNotContain("Засада врага");
        assertThat(player.getResponse().getHeader("ETag")).isNotNull().isEqualTo(etagBefore);
        String gm = chat("master").getResponse().getContentAsString();
        assertThat(gm).contains("🙈 ", "Засада врага", "is-hidden", "/reveal");

        RollLog roll = rollLogRepository.findByCampaignIdOrderByCreatedAtDescIdDesc(campaign.getId(), PageRequest.of(0, 1)).getFirst();
        mockMvc.perform(post("/campaign/" + campaign.getId() + "/rolls/" + roll.getId() + "/reveal").with(user("kolya")).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/campaign/" + campaign.getId() + "/rolls/" + roll.getId() + "/reveal").with(user("master")).with(csrf()));

        assertThat(chat("kolya").getResponse().getContentAsString()).contains("Засада врага");
    }

    @Test
    @DisplayName("Групповая проверка с «🙈 NPC скрыто»: бросок NPC не виден игроку и не входит в общую строку")
    void hiddenNpcInGroup() throws Exception {
        mockMvc.perform(post("/campaign/" + campaign.getId() + "/requests")
                .param("character", String.valueOf(kolya.getId()), String.valueOf(bandit.getId()))
                .param("what", "skill:awareness").param("difficulty", "15").param("hidden", "true")
                .with(user("master")).with(csrf()));
        clear();
        Long request = mockMvc.perform(get("/campaign/" + campaign.getId() + "/requests").with(user("kolya")))
                .andReturn().getResponse().getContentAsString().replaceAll("(?s).*/requests/(\\d+)/roll.*", "$1").transform(Long::valueOf);
        mockMvc.perform(post("/campaign/" + campaign.getId() + "/requests/" + request + "/roll").with(user("kolya")).with(csrf()));

        String feed = chat("kolya").getResponse().getContentAsString();
        assertThat(feed).doesNotContain("Бандит");
        assertThat(chatRepository.findByCampaignIdOrderByCreatedAtDescIdDesc(campaign.getId(), PageRequest.of(0, 5)))
                .extracting(ChatMessage::getText).anySatisfy(text -> assertThat(text).startsWith("Проверка Внимательность 15: Колясик")
                        .doesNotContain("Бандит"));
    }
}
