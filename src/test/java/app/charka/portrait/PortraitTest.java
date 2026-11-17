package app.charka.portrait;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.CharacterPortrait;
import app.charka.npc.NpcService;
import app.charka.repository.AppUserRepository;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterPortraitRepository;
import app.charka.repository.CharacterRepository;
import app.charka.security.UserService;
import app.charka.service.CharacterService;
import app.charka.service.campaign.CampaignExportService;
import app.charka.service.campaign.CampaignImportService;
import app.charka.support.CharkaSecuredTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@CharkaSecuredTest
@Transactional
@DisplayName("Портрет персонажа (PH-20)")
class PortraitTest {

    private static final byte[] PICTURE = {1, 2, 3, 4};

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private AppUserRepository userRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private CharacterService characterService;
    @Autowired private CharacterPortraitRepository portraitRepository;
    @Autowired private PortraitService portraitService;
    @Autowired private NpcService npcService;
    @Autowired private CampaignExportService exportService;
    @Autowired private CampaignImportService importService;
    @Autowired private EntityManager entityManager;

    private Campaign campaign;
    private Character kolya;
    private Character oleg;
    private Character bandit;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        Campaign created = new Campaign();
        created.setName("Ночной город");
        created.setSystem("CP2020");
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        created.setGm(userService.register("master", "secret1"));
        campaign = campaignRepository.save(created);
        kolya = member("Колясик", false);
        oleg = member("Олежа", false);
        bandit = member("Бандит", true);
        kolya.setPlayer(userService.register("kolya", "secret2"));
        oleg.setPlayer(userService.register("oleg", "secret3"));
        characterRepository.save(kolya);
        characterRepository.save(oleg);
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

    private String url(Character character) {
        return "/campaign/" + campaign.getId() + "/portraits/" + character.getId();
    }

    private MockMultipartFile file(String type, byte[] data) {
        return new MockMultipartFile("file", "portrait", type, data);
    }

    @Test
    @DisplayName("Игрок загружает своему, чужому — нельзя; лицо видят все за столом, с долгим кэшем")
    void uploadAndShow() throws Exception {
        mockMvc.perform(multipart("/character/" + kolya.getId() + "/portrait").file(file("image/webp", PICTURE))
                        .with(user("kolya")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(multipart("/character/" + kolya.getId() + "/portrait").file(file("image/webp", PICTURE))
                        .with(user("oleg")).with(csrf()))
                .andExpect(status().isNotFound());
        clear();

        mockMvc.perform(get(url(kolya)).with(user("oleg")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/webp"))
                .andExpect(content().bytes(PICTURE))
                .andExpect(header().string("Cache-Control", containsString("max-age")));
        mockMvc.perform(get(url(oleg)).with(user("kolya"))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Не картинка и больше 300 КБ — отказ с понятной подписью")
    void rejectsWrongFiles() throws Exception {
        mockMvc.perform(multipart("/character/" + kolya.getId() + "/portrait").file(file("image/gif", PICTURE))
                        .with(user("kolya")).with(csrf()))
                .andExpect(flash().attribute("errorMessage", "Портрет — картинка WebP, JPEG или PNG"));
        mockMvc.perform(multipart("/character/" + kolya.getId() + "/portrait")
                        .file(file("image/jpeg", new byte[PortraitService.MAX_BYTES + 1])).with(user("kolya")).with(csrf()))
                .andExpect(flash().attribute("errorMessage", startsWith("Портрет больше 300 КБ")));
        assertThat(portraitRepository.findById(kolya.getId())).isEmpty();
    }

    @Test
    @DisplayName("NPC вне боя игроку портрет не выдаёт, мастеру — да")
    void npcHiddenFromPlayers() throws Exception {
        portraitService.set(bandit.getId(), PICTURE, "image/png");
        clear();
        mockMvc.perform(get(url(bandit)).with(user("kolya"))).andExpect(status().isNotFound());
        mockMvc.perform(get(url(bandit)).with(user("master"))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Копии NPC и импорт кампании получают тот же портрет; «✕ Портрет» его убирает")
    void copiesExportAndRemove() throws Exception {
        portraitService.set(bandit.getId(), PICTURE, "image/png");
        clear();
        Character copy = npcService.copy(bandit.getId(), 1).getFirst();
        clear();
        assertThat(portraitRepository.findById(copy.getId())).get().extracting(CharacterPortrait::getData).isEqualTo(PICTURE);

        String json = exportService.exportCampaignToJson(campaignRepository.findById(campaign.getId()).orElseThrow());
        Campaign imported = importService.importCampaign(json);
        clear();
        Character importedBandit = characterRepository.findAll().stream()
                .filter(c -> c.getCampaign().getId().equals(imported.getId()) && c.getName().equals("Бандит"))
                .findFirst().orElseThrow();
        assertThat(portraitRepository.findById(importedBandit.getId())).get()
                .extracting(CharacterPortrait::getContentType).isEqualTo("image/png");

        mockMvc.perform(post("/character/" + bandit.getId() + "/portrait/delete").with(user("master")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(portraitRepository.findById(bandit.getId())).isEmpty();
    }
}
