package app.charka.request;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.ChatMessage;
import app.charka.model.RollRequest;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.repository.AppUserRepository;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.repository.ChatMessageRepository;
import app.charka.repository.RollRequestRepository;
import app.charka.security.UserService;
import app.charka.service.CharacterService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "charka.security.enabled=true")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Transactional
@DisplayName("Запросы бросков от мастера (A-07, A-08)")
class RollRequestTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private AppUserRepository userRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private CharacterService characterService;
    @Autowired private RollRequestRepository requestRepository;
    @Autowired private ChatMessageRepository chatRepository;
    @Autowired private EntityManager entityManager;

    @MockitoBean(answers = Answers.CALLS_REAL_METHODS) private DiceRoller dice;

    private Campaign campaign;
    private Character kolya;
    private Character olya;
    private Character bandit;
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
        kolya = member("Колясик", false);
        olya = member("Олежа", false);
        bandit = member("Бандит", true);
        userService.register("master", "secret1");
        kolya.setPlayer(userService.register("kolya", "secret2"));
        olya.setPlayer(userService.register("olya", "secret3"));
        characterRepository.save(kolya);
        characterRepository.save(olya);
        base = "/campaign/" + campaign.getId() + "/requests";
        clear();
        when(dice.roll(anyInt())).thenReturn(7);
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

    private String cards(String who, Long character) throws Exception {
        clear();
        return mockMvc.perform(get(base).param("character", character == null ? "" : String.valueOf(character)).with(user(who)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private RollRequest only(Character character) {
        return requestRepository.findByCampaignIdAndDoneAtIsNullOrderByIdAsc(campaign.getId()).stream()
                .filter(request -> request.getCharacter().getId().equals(character.getId())).findFirst().orElseThrow();
    }

    private List<String> feed() {
        clear();
        return chatRepository.findByCampaignIdOrderByCreatedAtDescIdDesc(campaign.getId(), PageRequest.of(0, 10))
                .stream().map(ChatMessage::getText).toList();
    }

    @Test
    @DisplayName("Запрос одному: у игрока карточка «Мастер просит», одно нажатие — итог в плашке, запрос ушёл")
    void onePlayerOneTap() throws Exception {
        mockMvc.perform(post(base).param("character", String.valueOf(kolya.getId())).param("what", "skill:awareness")
                .param("difficulty", "15").with(user("master")).with(csrf()));

        assertThat(cards("kolya", kolya.getId())).contains("Мастер просит: Внимательность 15", "🎲 Бросить");
        assertThat(cards("olya", null)).doesNotContain("Внимательность");
        assertThat(cards("master", null)).contains("ждёт: Внимательность 15", "🎲 За игрока");

        RollRequest request = only(kolya);
        mockMvc.perform(post(base + "/" + request.getId() + "/roll").with(user("olya")).with(csrf()));
        assertThat(only(kolya).isOpen()).as("чужой запрос игрок не бросит").isTrue();

        MvcResult rolled = mockMvc.perform(post(base + "/" + request.getId() + "/roll").with(user("kolya")).with(csrf())
                        .header("Referer", "http://localhost/character/" + kolya.getId()))
                .andReturn();
        assertThat(rolled.getResponse().getRedirectedUrl()).isEqualTo("/character/" + kolya.getId());
        assertThat((List<?>) rolled.getFlashMap().get("requestResults")).singleElement().asString()
                .startsWith("Колясик: Внимательность 15: ").containsAnyOf("успех", "провал");
        assertThat(cards("kolya", kolya.getId())).doesNotContain("Внимательность");
    }

    @Test
    @DisplayName("Форма мастера на странице кампании и в бою; у игрока — нет, но есть место для карточек")
    void pages() throws Exception {
        String gm = mockMvc.perform(get("/campaign/" + campaign.getId()).with(user("master")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(gm).contains("Попросить бросок", "value=\"skill:awareness\"", "value=\"save:STUN\"", "data-requests=");
        String player = mockMvc.perform(get("/campaign/" + campaign.getId()).with(user("kolya")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(player).doesNotContain("Попросить бросок").contains("data-requests=");
        String sheet = mockMvc.perform(get("/character/" + kolya.getId()).with(user("kolya")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(sheet).contains("data-requests=\"/campaign/" + campaign.getId() + "/requests?character=" + kolya.getId() + "\"");
    }

    @Test
    @DisplayName("Группой: NPC брошен сразу, игроки — по нажатию; когда брошены все — одна строка в ленте")
    void groupCheck() throws Exception {
        mockMvc.perform(post(base).param("character", String.valueOf(kolya.getId()), String.valueOf(olya.getId()), String.valueOf(bandit.getId()))
                .param("what", "skill:awareness").param("difficulty", "15").with(user("master")).with(csrf()));
        clear();
        assertThat(requestRepository.findByCampaignIdAndDoneAtIsNullOrderByIdAsc(campaign.getId())).hasSize(2);

        mockMvc.perform(post(base + "/" + only(kolya).getId() + "/roll").with(user("kolya")).with(csrf()));
        assertThat(feed()).noneMatch(text -> text.startsWith("Проверка"));
        mockMvc.perform(post(base + "/" + only(olya).getId() + "/roll").with(user("olya")).with(csrf()));

        assertThat(feed()).anySatisfy(text -> assertThat(text).startsWith("Проверка Внимательность 15: ")
                .contains("Колясик", "Олежа", "Бандит").containsAnyOf("✓", "✗"));
    }

    @Test
    @DisplayName("«Бросить за всех» и спасбросок: ничего не ждёт, итог спасброска — в ленте")
    void rollNowAndSave() throws Exception {
        MvcResult result = mockMvc.perform(post(base).param("character", String.valueOf(kolya.getId()), String.valueOf(olya.getId()))
                        .param("what", "save:STUN").param("rollNow", "true").with(user("master")).with(csrf()))
                .andReturn();
        clear();

        assertThat((List<?>) result.getFlashMap().get("requestResults")).hasSize(2);
        assertThat(requestRepository.findByCampaignIdAndDoneAtIsNullOrderByIdAsc(campaign.getId())).isEmpty();
        assertThat(feed()).anyMatch(text -> text.startsWith("🎲 Спасбросок от оглушения"))
                .anyMatch(text -> text.startsWith("Проверка Спасбросок от оглушения: "));
    }
}
