package app.charka.security;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.repository.AppUserRepository;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.service.CharacterService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "charka.security.enabled=true")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Transactional
@DisplayName("Приглашение по ссылке и QR-коду (A-02)")
class InviteTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private InviteService inviteService;
    @Autowired private AppUserRepository userRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private CharacterService characterService;
    @Autowired private EntityManager entityManager;

    private Campaign campaign;
    private Character free;
    private Character npc;
    private String token;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        Campaign created = new Campaign();
        created.setName("Ночной город");
        created.setSystem("CP2020");
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        campaign = campaignRepository.save(created);
        free = member("Колясик", false);
        npc = member("Бандит", true);
        userService.register("master", "secret1");
        userService.register("olya", "secret3");
        token = inviteService.token(campaign.getId());
        entityManager.flush();
        entityManager.clear();
    }

    private Character member(String name, boolean isNpc) {
        Character character = new Character();
        character.setName(name);
        character.setNpc(isNpc);
        return characterService.createInCampaign(campaign.getId(), character);
    }

    private Character reload(Character character) {
        entityManager.flush();
        entityManager.clear();
        return characterRepository.findById(character.getId()).orElseThrow();
    }

    @Nested
    @DisplayName("Игрок с телефона")
    class Player {

        @Test
        @DisplayName("Новый игрок: ссылка → вход с названием кампании → «Я новый игрок» (пароль один раз) → «Это я» → лист")
        void newPlayer() throws Exception {
            MockHttpSession session = new MockHttpSession();
            mockMvc.perform(get("/join/" + token).session(session)).andExpect(redirectedUrlPattern("**/login"));
            String login = mockMvc.perform(get("/login").session(session))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(login).contains("Ночной город", "Я новый игрок");

            // нажатие 1: регистрация из формы входа, без повтора пароля
            mockMvc.perform(post("/register").session(session).param("username", "kolya").param("password", "secret2").with(csrf()))
                    .andExpect(redirectedUrl("/join/" + token));
            String join = mockMvc.perform(get("/join/" + token).session(session))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(join).contains("Колясик", "Это я", "Создать по правилам").doesNotContain("Бандит");

            // нажатие 2: «Это я»
            mockMvc.perform(post("/join/" + token + "/claim/" + free.getId()).session(session).with(csrf()))
                    .andExpect(redirectedUrl("/character/" + free.getId()));
            assertThat(reload(free).getPlayer().getUsername()).isEqualTo("kolya");
            mockMvc.perform(get("/character/" + free.getId()).session(session)).andExpect(status().isOk());
        }

        @Test
        @DisplayName("Уже есть учётная запись: вход возвращает на приглашение")
        void existingPlayer() throws Exception {
            MockHttpSession session = new MockHttpSession();
            mockMvc.perform(get("/join/" + token).session(session)).andExpect(redirectedUrlPattern("**/login"));
            mockMvc.perform(post("/login").session(session).param("username", "olya").param("password", "secret3").with(csrf()))
                    .andExpect(redirectedUrlPattern("**/join/" + token + "*"));
        }

        @Test
        @DisplayName("Занятого и NPC не забрать")
        void taken() throws Exception {
            mockMvc.perform(post("/join/" + token + "/claim/" + free.getId()).with(user("olya")).with(csrf()))
                    .andExpect(redirectedUrl("/character/" + free.getId()));
            userService.register("kolya", "secret2");

            mockMvc.perform(post("/join/" + token + "/claim/" + free.getId()).with(user("kolya")).with(csrf()))
                    .andExpect(redirectedUrl("/join/" + token));
            mockMvc.perform(post("/join/" + token + "/claim/" + npc.getId()).with(user("kolya")).with(csrf()))
                    .andExpect(redirectedUrl("/join/" + token));
            assertThat(reload(free).getPlayer().getUsername()).isEqualTo("olya");
            assertThat(reload(npc).getPlayer()).isNull();
        }

        @Test
        @DisplayName("Мастер создания: открыт по приглашению, без него — 404; созданный — сразу игрока")
        void creation() throws Exception {
            MockHttpSession session = new MockHttpSession();
            mockMvc.perform(get("/campaign/" + campaign.getId() + "/characters/new").session(session).with(user("olya")))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get("/join/" + token).session(session).with(user("olya"))).andExpect(status().isOk());
            mockMvc.perform(get("/campaign/" + campaign.getId() + "/characters/new").session(session).with(user("olya")))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Мастер")
    class Master {

        @Test
        @DisplayName("На странице кампании QR-код и ссылка; игрок их не видит")
        void card() throws Exception {
            String page = mockMvc.perform(get("/campaign/" + campaign.getId()).with(user("master")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(page).contains("Пригласить игроков", "<svg", "/join/" + token);
        }

        @Test
        @DisplayName("Новая ссылка: старая — 404")
        void reset() throws Exception {
            mockMvc.perform(post("/campaign/" + campaign.getId() + "/invite/reset").with(user("master")).with(csrf()))
                    .andExpect(redirectedUrl("/campaign/" + campaign.getId() + "#invite"));
            mockMvc.perform(get("/join/" + token).with(user("olya"))).andExpect(status().isNotFound());
            assertThat(inviteService.token(campaign.getId())).isNotEqualTo(token);
        }
    }

    @Test
    @DisplayName("QR-код — SVG; адреса — с портом")
    void qrAndAddresses() {
        assertThat(inviteService.qrSvg("http://192.168.1.5:8080/join/abc")).startsWith("<svg").contains("<path");
        assertThat(inviteService.addresses("http", "192.168.1.5", 8080)).first()
                .satisfies(address -> assertThat(address.url()).isEqualTo("http://192.168.1.5:8080"));
        assertThat(InviteService.Network.of("Hyper-V Virtual Ethernet Adapter #2")).isEqualTo(InviteService.Network.VIRTUAL);
        assertThat(InviteService.Network.of("Famatech Radmin VPN Ethernet Adapter")).isEqualTo(InviteService.Network.VPN);
        assertThat(InviteService.Network.of("WireGuard Tunnel")).isEqualTo(InviteService.Network.VPN);
        assertThat(InviteService.Network.of("Intel(R) Ethernet Connection (2) I218-V")).isEqualTo(InviteService.Network.LAN);
    }
}
