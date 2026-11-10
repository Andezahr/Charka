package app.charka.undo;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.repository.AppUserRepository;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.service.CharacterService;
import app.charka.security.UserService;
import app.charka.system.cp2020.Cp2020HealthStrategy;
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
@DisplayName("Отмена (A-01): кто может отменить")
class UndoRightsTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private AppUserRepository userRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private CharacterService characterService;
    @Autowired private EntityManager entityManager;

    private Campaign campaign;
    private Character mine;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        Campaign created = new Campaign();
        created.setName("Ночной город");
        created.setSystem("CP2020");
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        campaign = campaignRepository.save(created);
        mine = member("Колясик");
        Character other = member("Олежа");
        userService.register("master", "secret1");
        mine.setPlayer(userService.register("kolya", "secret2"));
        other.setPlayer(userService.register("olya", "secret3"));
        characterRepository.save(mine);
        characterRepository.save(other);
        entityManager.flush();
        entityManager.clear();
    }

    private Character member(String name) {
        Character character = new Character();
        character.setName(name);
        return characterService.createInCampaign(campaign.getId(), character);
    }

    private UndoService.Offer damageMine() throws Exception {
        Long counter = characterRepository.findById(mine.getId()).orElseThrow().getCounters().stream()
                .filter(c -> Cp2020HealthStrategy.TYPE.equals(c.getType())).findFirst().orElseThrow().getId();
        MvcResult result = mockMvc.perform(post("/character/" + mine.getId() + "/counters/" + counter + "/health/damage")
                        .param("amount", "5").with(user("kolya")).with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn();
        return (UndoService.Offer) result.getFlashMap().get(UndoInterceptor.OFFER);
    }

    private MvcResult undo(UndoService.Offer offer, String who) throws Exception {
        return mockMvc.perform(post("/campaign/" + campaign.getId() + "/undo/" + offer.id()).with(user(who)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn();
    }

    @Test
    @DisplayName("Чужое действие игрок не отменит и кнопки в ленте не видит; своё — отменяет")
    void otherPlayer() throws Exception {
        UndoService.Offer offer = damageMine();

        assertThat((String) undo(offer, "olya").getFlashMap().get("undoError")).contains("тот, кто это сделал");
        String feed = mockMvc.perform(get("/campaign/" + campaign.getId() + "/chat").with(user("olya")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(feed).doesNotContain("/undo/");

        assertThat(undo(offer, "kolya").getFlashMap().get("undoDone")).isEqualTo("Отменено: Колясик: урон");
    }

    @Test
    @DisplayName("Мастер отменяет действие игрока")
    void master() throws Exception {
        UndoService.Offer offer = damageMine();

        assertThat(undo(offer, "master").getFlashMap().get("undoDone")).isEqualTo("Отменено: Колясик: урон");
    }
}
