package app.charka.security;

import app.charka.model.AppUser;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "charka.security.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Пользователи и права (U-03)")
class SecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private AppUserRepository userRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private CharacterService characterService;
    @Autowired private EntityManager entityManager;

    private Campaign campaign;
    private Character mine;
    private Character other;

    @BeforeEach
    void setUp() {
        // данные до пользователей: кампания без мастера
        userRepository.deleteAll();
        Campaign created = new Campaign();
        created.setName("Ночной город");
        created.setSystem("CP2020");
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        campaign = campaignRepository.save(created);
        mine = member("Колясик");
        other = member("Олежа");
        userService.register("master", "secret1");
        AppUser player = userService.register("Kolya", "secret2");
        userService.register("stranger", "secret3");
        mine.setPlayer(player);
        characterRepository.save(mine);
        entityManager.flush();
        entityManager.clear();
    }

    private Character member(String name) {
        Character character = new Character();
        character.setName(name);
        return characterService.createInCampaign(campaign.getId(), character);
    }

    @Nested
    @DisplayName("Вход и регистрация")
    class Login {

        @Test
        @DisplayName("Без входа — на страницу входа; первый зарегистрированный — мастер старых кампаний")
        void anonymous() throws Exception {
            mockMvc.perform(get("/")).andExpect(redirectedUrlPattern("**/login"));
            mockMvc.perform(get("/login")).andExpect(status().isOk());

            assertThat(campaignRepository.findById(campaign.getId()).orElseThrow().getGm().getUsername()).isEqualTo("master");
        }

        @Test
        @DisplayName("Логин строчными, занятый и короткий пароль — отказ; вход формой по паролю")
        void register() throws Exception {
            assertThat(userRepository.findByUsername("kolya")).isPresent();
            assertThatThrownBy(() -> userService.register("KOLYA", "another1")).hasMessageContaining("занят");
            assertThatThrownBy(() -> userService.register("new_one", "123")).hasMessageContaining("Пароль");
            assertThatThrownBy(() -> userService.register("я", "secret12")).hasMessageContaining("Логин");

            mockMvc.perform(post("/login").param("username", "kolya").param("password", "secret2").with(csrf()))
                    .andExpect(redirectedUrl("/"));
            mockMvc.perform(post("/login").param("username", "kolya").param("password", "wrong").with(csrf()))
                    .andExpect(redirectedUrl("/login?error"));
        }

        @Test
        @DisplayName("Регистрация формой сразу входит; без CSRF-токена запись не проходит")
        void registerForm() throws Exception {
            mockMvc.perform(post("/register").param("username", "newbie").param("password", "secret9").param("repeat", "secret9").with(csrf()))
                    .andExpect(redirectedUrl("/"));
            mockMvc.perform(post("/campaigns").param("name", "x").param("system", "CP2020").with(user("master")))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Права")
    class Rights {

        @Test
        @DisplayName("Мастер видит всё в кампании")
        void gm() throws Exception {
            mockMvc.perform(get("/campaign/" + campaign.getId()).with(user("master")))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("/gm\">Мастер</a>")))
                    .andExpect(content().string(containsString("Олежа")));
            mockMvc.perform(get("/character/" + other.getId()).with(user("master"))).andExpect(status().isOk());
            mockMvc.perform(get("/campaign/" + campaign.getId() + "/maps").with(user("master"))).andExpect(status().isOk());
        }

        @Test
        @DisplayName("Игрок: свой лист, страница кампании без инструментов мастера, бой; чужой лист и карты — 404")
        void player() throws Exception {
            String base = "/campaign/" + campaign.getId();
            mockMvc.perform(get(base).with(user("kolya")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Колясик")))
                    .andExpect(content().string(not(containsString("/gm\">Мастер</a>"))))
                    .andExpect(content().string(not(containsString("Олежа"))));
            mockMvc.perform(get("/character/" + mine.getId()).with(user("kolya"))).andExpect(status().isOk());
            mockMvc.perform(get("/character/" + other.getId()).with(user("kolya"))).andExpect(status().isNotFound());
            mockMvc.perform(get("/api/characters/" + other.getId() + "/skills").with(user("kolya"))).andExpect(status().isNotFound());
            mockMvc.perform(get(base + "/encounter").with(user("kolya"))).andExpect(status().isOk());
            mockMvc.perform(post(base + "/encounter/start").param("character", String.valueOf(mine.getId())).with(user("kolya")).with(csrf()))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get(base + "/maps").with(user("kolya"))).andExpect(status().isNotFound());
            mockMvc.perform(get("/api/campaigns/" + campaign.getId() + "/download").with(user("kolya"))).andExpect(status().isNotFound());
            mockMvc.perform(get("/").with(user("kolya"))).andExpect(content().string(containsString("Ночной город")));
        }

        @Test
        @DisplayName("Чужой ничего не видит; кампанию из списка не показывают")
        void stranger() throws Exception {
            mockMvc.perform(get("/campaign/" + campaign.getId()).with(user("stranger"))).andExpect(status().isNotFound());
            mockMvc.perform(get("/character/" + mine.getId()).with(user("stranger"))).andExpect(status().isNotFound());
            mockMvc.perform(get("/").with(user("stranger"))).andExpect(content().string(not(containsString("Ночной город"))));
        }

        @Test
        @DisplayName("Мастер отдаёт персонажа по логину; игрок пишет в чат только от себя")
        void assignAndChat() throws Exception {
            String base = "/campaign/" + campaign.getId();
            mockMvc.perform(post(base + "/characters/" + other.getId() + "/player").param("username", "Kolya").with(user("master")).with(csrf()))
                    .andExpect(redirectedUrl(base));
            entityManager.flush();
            entityManager.clear();
            assertThat(characterRepository.findById(other.getId()).orElseThrow().getPlayer().getUsername()).isEqualTo("kolya");

            mockMvc.perform(post(base + "/chat").param("text", "Я мастер").with(user("kolya")).with(csrf()));
            mockMvc.perform(post(base + "/chat").param("character", String.valueOf(mine.getId())).param("text", "Бегу").with(user("kolya")).with(csrf()));
            mockMvc.perform(get(base + "/chat").with(user("kolya")))
                    .andExpect(content().string(containsString("Бегу")))
                    .andExpect(content().string(not(containsString("Я мастер"))));
        }

        @Test
        @DisplayName("Новая кампания — мастер тот, кто создал")
        void creator() throws Exception {
            mockMvc.perform(post("/campaigns").param("name", "Моя").param("system", "CP2020").with(user("stranger")).with(csrf()))
                    .andExpect(status().is3xxRedirection());
            entityManager.flush();
            assertThat(campaignRepository.findAll()).filteredOn(c -> "Моя".equals(c.getName()))
                    .singleElement().satisfies(c -> assertThat(c.getGm().getUsername()).isEqualTo("stranger"));
        }
    }
}
