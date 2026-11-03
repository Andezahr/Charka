package app.charka.chat;

import app.charka.dice.DiceService;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.repository.CampaignRepository;
import app.charka.service.CharacterService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Чат кампании (U-06)")
class ChatServiceTest {

    @Autowired private ChatService chatService;
    @Autowired private DiceService diceService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterService characterService;
    @Autowired private EntityManager entityManager;
    @Autowired private MockMvc mockMvc;

    private Campaign campaign;
    private Character player;

    @BeforeEach
    void setUp() {
        campaign = campaign();
        Character character = new Character();
        character.setName("Колясик");
        player = characterService.createInCampaign(campaign.getId(), character);
    }

    private Campaign campaign() {
        Campaign created = new Campaign();
        created.setName("Тест");
        created.setSystem("CP2020");
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 2));
        return campaignRepository.save(created);
    }

    @Nested
    @DisplayName("Сообщения")
    class Messages {

        @Test
        @DisplayName("От персонажа и от мастера, с игровой датой; лента — вперемешку с бросками, новые сверху")
        void feed() {
            chatService.post(campaign.getId(), player.getId(), "  Бегу к машине ");
            diceService.rollForCharacter(player.getId(), "1d10", "Атака");
            chatService.post(campaign.getId(), null, "Машина взорвалась");
            entityManager.flush();

            List<ChatService.Entry> feed = chatService.feed(campaign.getId(), 10);

            assertThat(feed).extracting(ChatService.Entry::who).containsExactly("Мастер", "Колясик", "Колясик");
            assertThat(feed.get(1).roll()).isNotNull();
            assertThat(feed.get(2).text()).isEqualTo("Бегу к машине");
            assertThat(chatService.feed(campaign.getId(), 2)).hasSize(2);
        }

        @Test
        @DisplayName("Пустое, длинное и от чужого персонажа — отказ")
        void refused() {
            assertThatThrownBy(() -> chatService.post(campaign.getId(), null, "  ")).hasMessageContaining("Пустое");
            assertThatThrownBy(() -> chatService.post(campaign.getId(), null, "x".repeat(501))).hasMessageContaining("500");
            Character stranger = new Character();
            stranger.setName("Чужой");
            Character other = characterService.createInCampaign(campaign().getId(), stranger);
            assertThatThrownBy(() -> chatService.post(campaign.getId(), other.getId(), "привет")).hasMessageContaining("другой кампании");
        }
    }

    @Nested
    @DisplayName("Страницы")
    class Pages {

        @Test
        @DisplayName("Отправка возвращает туда, откуда писали; лента отдаётся с ETag, без изменений — 304")
        void flow() throws Exception {
            String base = "/campaign/" + campaign.getId();
            mockMvc.perform(post(base + "/chat").param("character", String.valueOf(player.getId())).param("text", "Прикрой")
                            .param("returnTo", base + "/encounter"))
                    .andExpect(redirectedUrl(base + "/encounter#chat"));
            mockMvc.perform(post(base + "/chat").param("text", "x").param("returnTo", "https://evil.example"))
                    .andExpect(redirectedUrl(base + "#chat"));

            String etag = mockMvc.perform(get(base + "/chat")).andExpect(status().isOk())
                    .andExpect(content().string(containsString("Прикрой")))
                    .andReturn().getResponse().getHeader("ETag");
            mockMvc.perform(get(base + "/chat").header("If-None-Match", etag)).andExpect(status().isNotModified());
            mockMvc.perform(get(base)).andExpect(content().string(containsString("data-chat")));
        }
    }
}
