package app.charka.controller;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.model.Money;
import app.charka.repository.CampaignRepository;
import app.charka.repository.ItemRepository;
import app.charka.service.CharacterService;
import app.charka.service.ItemService;
import app.charka.service.MoneyService;
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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
@DisplayName("Принадлежность ресурсов персонажу (U-02)")
class OwnershipInterceptorTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterService characterService;
    @Autowired private ItemService itemService;
    @Autowired private ItemRepository itemRepository;
    @Autowired private MoneyService moneyService;
    @Autowired private EntityManager entityManager;

    private Character owner;
    private Character stranger;
    private Long ownerInventory;
    private Long strangerInventory;
    private Item item;

    @BeforeEach
    void setUp() {
        Campaign created = new Campaign();
        created.setName("Тест");
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        Campaign campaign = campaignRepository.save(created);
        owner = member(campaign, "Хозяин");
        stranger = member(campaign, "Чужой");
        ownerInventory = owner.getInventories().getFirst().getId();
        strangerInventory = stranger.getInventories().getFirst().getId();
        Item added = new Item();
        added.setName("Часы");
        added.setQuantity(1);
        item = itemService.create(ownerInventory, added);
        entityManager.flush();
    }

    private Character member(Campaign campaign, String name) {
        Character character = new Character();
        character.setName(name);
        return characterService.createInCampaign(campaign.getId(), character);
    }

    @Test
    @DisplayName("Чужой предмет через свой адрес не удалить: 404, предмет на месте")
    void foreignItem() throws Exception {
        mockMvc.perform(post("/character/" + stranger.getId() + "/inventories/" + strangerInventory + "/items/" + item.getId() + "/delete"))
                .andExpect(status().isNotFound());

        assertThat(itemRepository.findById(item.getId())).isPresent();
    }

    @Test
    @DisplayName("Свой предмет не перенести в чужой инвентарь")
    void moveToForeignInventory() throws Exception {
        mockMvc.perform(post("/character/" + owner.getId() + "/inventories/" + ownerInventory + "/items/" + item.getId() + "/move")
                        .param("targetInventoryId", String.valueOf(strangerInventory)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Чужой инвентарь не переименовать")
    void foreignInventory() throws Exception {
        mockMvc.perform(post("/character/" + owner.getId() + "/inventories/" + strangerInventory + "/edit").param("name", "Моё"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Чужие деньги не удалить; свои — можно")
    void money() throws Exception {
        Money money = new Money();
        money.setName("Заначка");
        money.setAmount(100L);
        Long id = moneyService.create(owner.getId(), money).getId();
        entityManager.flush();

        mockMvc.perform(post("/character/" + stranger.getId() + "/money/" + id + "/delete")).andExpect(status().isNotFound());
        mockMvc.perform(post("/character/" + owner.getId() + "/money/" + id + "/delete")).andExpect(status().is3xxRedirection());
    }
}
