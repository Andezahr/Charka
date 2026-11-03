package app.charka.controller;

import app.charka.model.Campaign;
import app.charka.model.Character;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Transactional
@DisplayName("Лист: шапка состояния вместо вкладки «Счётчики» (R-05, R-06)")
class SheetLayoutTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterService characterService;
    @Autowired private EntityManager entityManager;

    private Character character;

    @BeforeEach
    void setUp() {
        Campaign campaign = new Campaign();
        campaign.setName("Тест");
        campaign.setSystem("CP2020");
        campaign.setStartDate(LocalDate.of(2037, 1, 1));
        campaign.setCurrentDate(LocalDate.of(2037, 1, 1));
        campaign = campaignRepository.save(campaign);
        Character created = new Character();
        created.setName("Колясик");
        character = characterService.createInCampaign(campaign.getId(), created);
        entityManager.flush();
        entityManager.clear();
    }

    private String sheet() throws Exception {
        entityManager.flush();
        entityManager.clear();
        return mockMvc.perform(get("/character/" + character.getId())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Здоровье, броня и статы — строками на листе, урон наносится оттуда; плиток и вкладки «Счётчики» нет")
    void stateOnSheet() throws Exception {
        String html = sheet();
        assertThat(html).contains("id=\"state-health\"", "id=\"state-armor\"", "id=\"state-stats\"", "Нанести", "id=\"counters\"", "＋ Счётчик")
                .doesNotContain("stat-label\">Инвентарей", "/counters\" class=\"\">Счётчики</a>");
        assertThat(html).doesNotContainPattern("<a[^>]*/counters\"[^>]*>Счётчики</a>");
        // R-09: журнал здоровья — в блоке состояния, отдельной карточки нет
        assertThat(html).contains("Журнал здоровья (0)", "＋ Запись").doesNotContain("<section class=\"card\" id=\"wounds\"");
    }

    @Test
    @DisplayName("Старый адрес счётчиков ведёт на лист; урон с листа возвращает к здоровью")
    void redirects() throws Exception {
        mockMvc.perform(get("/character/" + character.getId() + "/counters"))
                .andExpect(redirectedUrl("/character/" + character.getId() + "#counters"));
        String html = sheet();
        String damage = html.replaceAll("(?s).*action=\"(/character/" + character.getId() + "/counters/\\d+/health/damage)\".*", "$1");
        mockMvc.perform(post(damage).param("amount", "5").param("location", "TORSO").param("damageType", "NORMAL"))
                .andExpect(redirectedUrl("/character/" + character.getId() + "#state-health"));
        assertThat(sheet()).contains("Лёгкое");
    }

    @Test
    @DisplayName("R-07: первое оружие — основное с навыком атаки, остальное — в «Запасном»; «Сделать основным» меняет их местами")
    void weapons() throws Exception {
        Long inventoryId = characterService.getById(character.getId()).getInventories().get(0).getId();
        for (String key : new String[]{"militech_arms_avenger", "kendachi_monoknife"}) {
            mockMvc.perform(post("/character/" + character.getId() + "/inventories/" + inventoryId + "/items/catalog")
                    .param("catalogKey", key).param("quantity", "1"));
        }
        String html = sheet();
        String weapons = html.substring(html.indexOf("id=\"weapons\""), html.indexOf("id=\"inventories\""));
        assertThat(weapons).contains("Militech Arms Avenger", "Пистолеты 0 + REF", "Запасное: Kendachi Monoknife", "Сделать основным")
                .doesNotContain("Лучший навык 0");
        assertThat(weapons.indexOf("Militech Arms Avenger")).isLessThan(weapons.indexOf("Запасное"));
        assertThat(html.substring(html.indexOf("id=\"inventories\""))).contains("— в карточке «Оружие»");

        Long knife = characterService.getById(character.getId()).getInventories().get(0).getContent().stream()
                .filter(item -> item.getName().contains("Monoknife")).findFirst().orElseThrow().getId();
        mockMvc.perform(post("/character/" + character.getId() + "/main-weapon").param("item", String.valueOf(knife)))
                .andExpect(redirectedUrl("/character/" + character.getId() + "#weapons"));
        weapons = sheet();
        weapons = weapons.substring(weapons.indexOf("id=\"weapons\""), weapons.indexOf("id=\"inventories\""));
        assertThat(weapons.indexOf("Kendachi Monoknife")).isLessThan(weapons.indexOf("Запасное"));
        assertThat(weapons).contains("Запасное: Militech Arms Avenger");
    }

    @Test
    @DisplayName("R-07: чужой предмет основным не сделать")
    void foreignWeapon() throws Exception {
        mockMvc.perform(post("/character/" + character.getId() + "/main-weapon").param("item", "999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("R-09a: «Частые навыки» — последние проверенные, а закреплённые ☆ их заменяют")
    void frequentSkills() throws Exception {
        assertThat(sheet()).doesNotContain("id=\"frequent\"");
        Long campaignId = characterService.getById(character.getId()).getCampaign().getId();
        mockMvc.perform(post("/campaign/" + campaignId + "/chat/check").param("character", String.valueOf(character.getId()))
                .param("skill", "awareness").param("returnTo", "/character/" + character.getId()));
        String html = sheet();
        assertThat(html).contains("id=\"frequent\"", "🎲 Внимательность", "data-opens-table");

        mockMvc.perform(post("/character/" + character.getId() + "/skills/pin").param("key", "handgun").param("anchor", "skill-handgun"))
                .andExpect(redirectedUrl("/character/" + character.getId() + "/skills#skill-handgun"));
        html = sheet();
        String frequent = html.substring(html.indexOf("id=\"frequent\""));
        assertThat(frequent).contains("🎲 Пистолеты").doesNotContain("🎲 Внимательность");
        assertThat(mockMvc.perform(get("/character/" + character.getId() + "/skills")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).contains("is-pinned");

        mockMvc.perform(post("/character/" + character.getId() + "/skills/pin").param("key", "handgun"));
        assertThat(sheet()).contains("🎲 Внимательность");
    }
}
