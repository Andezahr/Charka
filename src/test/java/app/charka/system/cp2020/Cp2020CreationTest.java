package app.charka.system.cp2020;

import app.charka.creation.CharacterCreation;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.CharacterSkill;
import app.charka.model.Money;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
@DisplayName("Мастер создания персонажа CP2020 (P-01)")
class Cp2020CreationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private Cp2020Creation creation;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private EntityManager entityManager;

    @MockitoBean(answers = Answers.CALLS_REAL_METHODS) private DiceRoller dice;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        Campaign created = new Campaign();
        created.setName("Тест");
        created.setSystem("CP2020");
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        campaign = campaignRepository.save(created);
    }

    /** Major Hero: 80 очков; Соло: боевое чутьё 6, карате вместо драки 4, остальное по 4 — 40 */
    private Map<String, String> solo() {
        Map<String, String> params = new HashMap<>();
        params.put("name", "Морган");
        params.put("method", "Major Hero");
        params.put("points", "80");
        int[] values = {8, 10, 8, 9, 8, 9, 10, 9, 9};
        for (int i = 0; i < Cp2020Creation.STATS.size(); i++) {
            params.put("stat." + Cp2020Creation.STATS.get(i), String.valueOf(values[i]));
        }
        params.put("role", "solo");
        params.put("career.0", "6");
        params.put("slot.3", "karate");
        for (int i = 1; i < 10; i++) {
            params.putIfAbsent("career." + i, "4");
        }
        params.put("career.9", "2");
        params.put("pickup.0.skill", "driving");
        params.put("pickup.0.level", "5");
        return params;
    }

    @Test
    @DisplayName("Создаёт: статы, роль, карьера с выбором, хобби, деньги по таблице × 1D6/3")
    void create() {
        // 1D6 = 6 → 2 месяца; Combat Sense 6 → 3000 в месяц
        when(dice.roll(anyInt())).thenReturn(6);
        Character created = creation.create(campaign, solo());
        entityManager.flush();
        entityManager.clear();

        Character loaded = characterRepository.findById(created.getId()).orElseThrow();
        Counter stats = loaded.getCounters().stream().filter(c -> Cp2020StatsStrategy.TYPE.equals(c.getType())).findFirst().orElseThrow();
        assertThat(stats.getAttribute(Cp2020StatsStrategy.REF, 0)).isEqualTo(10);
        assertThat(loaded.getRoles()).containsExactly("solo");
        Map<String, Integer> skills = loaded.getSkills().stream().collect(Collectors.toMap(CharacterSkill::getSkillCode, CharacterSkill::getLevel));
        assertThat(skills).containsEntry("combat_sense", 6).containsEntry("karate", 4).containsEntry("driving", 5).doesNotContainKey("brawling");
        assertThat(loaded.getMoneyList()).extracting(Money::getAmount).containsExactly(6000L);
    }

    @Test
    @DisplayName("Отказы: не все очки, больше 40 карьерных, хобби на карьерный навык, больше REF + INT хобби")
    void refuse() {
        Map<String, String> params = solo();
        params.put("stat.int", "7");
        assertThatThrownBy(() -> creation.create(campaign, params)).hasMessageContaining("Распределено 79");

        Map<String, String> career = solo();
        career.put("career.9", "3");
        assertThatThrownBy(() -> creation.create(campaign, career)).hasMessageContaining("41 из 40");

        Map<String, String> pickup = solo();
        pickup.put("pickup.0.skill", "handgun");
        assertThatThrownBy(() -> creation.create(campaign, pickup)).hasMessageContaining("карьерный");

        Map<String, String> tooMuch = solo();
        tooMuch.put("pickup.0.level", "10");
        tooMuch.put("pickup.1.skill", "stealth_x");
        tooMuch.put("pickup.1.level", "0");
        tooMuch.put("pickup.2.skill", "swimming");
        tooMuch.put("pickup.2.level", "9");
        assertThatThrownBy(() -> creation.create(campaign, tooMuch)).hasMessageContaining("19 из 18");

        Map<String, String> wrongCinematic = solo();
        wrongCinematic.put("points", "85");
        assertThatThrownBy(() -> creation.create(campaign, wrongCinematic)).hasMessageContaining("80");
    }

    @Test
    @DisplayName("Быстрый способ: 1D10 на стат, 2 и меньше перебрасываются; случайный — 9D10")
    void roll() {
        when(dice.roll(anyInt())).thenReturn(1, 2, 7, 3, 3, 3, 3, 3, 3, 3, 10);
        CharacterCreation.Roll fast = creation.roll(campaign, "FAST");
        assertThat(fast.rolls()).containsExactly(7, 3, 3, 3, 3, 3, 3, 3, 10);

        when(dice.roll(anyInt())).thenReturn(5);
        assertThat(creation.roll(campaign, "RANDOM").total()).isEqualTo(45);
    }

    @Test
    @DisplayName("Страница и маршруты: ссылка в кампании, бросок JSON, создание ведёт на лист")
    void pages() throws Exception {
        String campaignPage = mockMvc.perform(get("/campaign/" + campaign.getId()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(campaignPage).contains("/characters/new");
        String page = mockMvc.perform(get("/campaign/" + campaign.getId() + "/characters/new"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(page).contains("data-creation").contains("Major Hero").contains("Драка или боевое искусство");

        when(dice.roll(anyInt())).thenReturn(4);
        String roll = mockMvc.perform(post("/campaign/" + campaign.getId() + "/characters/new/roll").param("method", "RANDOM"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(roll).contains("\"total\":36");

        var request = post("/campaign/" + campaign.getId() + "/characters/new");
        solo().forEach(request::param);
        mockMvc.perform(request).andExpect(redirectedUrlPattern("/character/*"));

        var bad = post("/campaign/" + campaign.getId() + "/characters/new").param("name", "Без всего");
        mockMvc.perform(bad).andExpect(flash().attributeExists("errorMessage"));
    }

    @Autowired private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Test
    @DisplayName("Лайфпас: возраст 2D6+16 или свой, событие на каждый год после 16, языки происхождения")
    void lifepath() {
        when(dice.roll(anyInt())).thenReturn(4);
        Cp2020Lifepath.Result chosen = Cp2020Lifepath.generate(20, dice);
        assertThat(chosen.age()).isEqualTo(20);
        assertThat(chosen.sections()).extracting(Cp2020Lifepath.Section::title)
                .containsExactly("Стиль", "Происхождение", "Семья", "Мотивация", "События, возраст 20");
        assertThat(chosen.sections().get(4).lines()).hasSize(4);
        assertThat(chosen.languages()).isNotEmpty();

        when(dice.roll(anyInt())).thenReturn(1);
        assertThat(Cp2020Lifepath.generate(null, dice).age()).isEqualTo(18);
    }

    @Test
    @DisplayName("Создание с лайфпасом: заметки «Лайфпас», родной язык +8, деньги и потеря стата")
    void createWithLifepath() throws Exception {
        Cp2020Lifepath.Result lifepath = new Cp2020Lifepath.Result(18,
                java.util.List.of(new Cp2020Lifepath.Section("Стиль", java.util.List.of("Одежда: джинсы")),
                        new Cp2020Lifepath.Section("События, возраст 18", java.util.List.of("17: Ничего", "18: Удача: 300 eb."))),
                java.util.List.of(new Cp2020Lifepath.Language("русский", "slavic")),
                java.util.List.of(new Cp2020Lifepath.Effect("Удачное дело", null, 0, 300),
                        new Cp2020Lifepath.Effect("Болезнь", Cp2020StatsStrategy.REF, -1, 0)),
                java.util.List.of(new Cp2020Lifepath.Person("ENEMY", "Враг из прошлого", "Враг (мужчина, бывший друг): увёл подругу")));
        Map<String, String> params = solo();
        params.put("lifepath", objectMapper.writeValueAsString(lifepath));
        params.put("nativeLanguage", "русский|slavic");
        when(dice.roll(anyInt())).thenReturn(6);

        Character created = creation.create(campaign, params);
        entityManager.flush();
        entityManager.clear();

        Character loaded = characterRepository.findById(created.getId()).orElseThrow();
        assertThat(loaded.getNotes()).extracting(app.charka.model.notes.Note::getName).containsExactlyInAnyOrder("Стиль", "События, возраст 18");
        assertThat(loaded.getNotes()).allSatisfy(note -> assertThat(note.getCategories()).extracting("name").containsExactly("Лайфпас"));
        assertThat(loaded.getSkills()).anySatisfy(skill -> {
            assertThat(skill.getCustomName()).isEqualTo("Русский");
            assertThat(skill.getLevel()).isEqualTo(8);
        });
        assertThat(loaded.getMoneyList()).extracting(Money::getAmount).containsExactlyInAnyOrder(6000L, 300L);
        Counter stats = loaded.getCounters().stream().filter(c -> Cp2020StatsStrategy.TYPE.equals(c.getType())).findFirst().orElseThrow();
        assertThat(stats.getAttribute(Cp2020StatsStrategy.REF, 0)).isEqualTo(9);
        assertThat(loaded.getContacts()).singleElement().satisfies(contact -> {
            assertThat(contact.getRelation()).isEqualTo(app.charka.model.Contact.Relation.ENEMY);
            assertThat(contact.getNote()).contains("увёл подругу");
        });
    }
}
