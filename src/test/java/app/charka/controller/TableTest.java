package app.charka.controller;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.ChatMessage;
import app.charka.model.RollLog;
import app.charka.repository.CampaignRepository;
import app.charka.repository.ChatMessageRepository;
import app.charka.repository.RollLogRepository;
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
import org.springframework.transaction.annotation.Transactional;

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
@DisplayName("«Стол»: чат и кубики одним блоком (R-04)")
class TableTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterService characterService;
    @Autowired private RollLogRepository rollLogRepository;
    @Autowired private ChatMessageRepository chatRepository;
    @Autowired private EntityManager entityManager;

    private Campaign campaign;
    private Character kolya;
    private String base;

    @BeforeEach
    void setUp() {
        Campaign created = new Campaign();
        created.setName("Тест");
        created.setSystem("CP2020");
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        campaign = campaignRepository.save(created);
        Character character = new Character();
        character.setName("Колясик");
        kolya = characterService.createInCampaign(campaign.getId(), character);
        base = "/campaign/" + campaign.getId() + "/chat";
    }

    private RollLog lastRoll() {
        entityManager.flush();
        entityManager.clear();
        return rollLogRepository.findByCampaignIdOrderByCreatedAtDescIdDesc(campaign.getId(), PageRequest.of(0, 1)).getFirst();
    }

    @Test
    @DisplayName("Текст с кубиков — бросок с подписью от персонажа; быстрая кнопка — бросок; остальное — сообщение; возврат на лист")
    void rollsAndMessages() throws Exception {
        mockMvc.perform(post(base).param("character", String.valueOf(kolya.getId())).param("text", "2d6+3 Атака")
                        .param("returnTo", "/character/" + kolya.getId()))
                .andExpect(redirectedUrl("/character/" + kolya.getId() + "#chat"));
        RollLog roll = lastRoll();
        assertThat(roll.getExpression()).isEqualToIgnoringCase("2d6+3");
        assertThat(roll.getLabel()).isEqualTo("Атака");
        assertThat(roll.getCharacter().getName()).isEqualTo("Колясик");

        mockMvc.perform(post(base).param("quick", "d10"));
        assertThat(lastRoll().getExpression()).isEqualToIgnoringCase("d10");

        mockMvc.perform(post(base).param("text", "d10 — это кубик").param("returnTo", "https://evil.example/x"))
                .andExpect(redirectedUrl("/campaign/" + campaign.getId() + "#chat"));
        mockMvc.perform(post(base).param("text", "Бегу к машине"));
        entityManager.flush();
        assertThat(chatRepository.findByCampaignIdOrderByCreatedAtDescIdDesc(campaign.getId(), PageRequest.of(0, 5)))
                .extracting(ChatMessage::getText).contains("Бегу к машине");
        assertThat(ChatController.rollOf("подсказка 2d6")).isNull();
        assertThat(ChatController.rollOf("3d6")).containsExactly("3d6", null);
    }

    @Test
    @DisplayName("Карточки «Кубики» нет на листе и в обзоре; «Стол» — кнопкой в шапке и панелью с быстрыми кубиками системы")
    void panel() throws Exception {
        String sheet = mockMvc.perform(get("/character/" + kolya.getId())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(sheet).doesNotContain("id=\"dice\"").contains("data-table-open", base + "/panel");
        String overview = mockMvc.perform(get("/campaign/" + campaign.getId())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(overview).doesNotContain("id=\"dice\"").contains("Стол: чат и броски", ">d10<", "Проверка ±Мод · DC");

        String panel = mockMvc.perform(get(base + "/panel").param("returnTo", "/character/" + kolya.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(panel).contains("data-chat=", "Сообщение или 2d6+3", ">d10<", "value=\"/character/" + kolya.getId() + "\"");
    }
}
