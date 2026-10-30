package app.charka.npc;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.service.CharacterService;
import app.charka.system.cp2020.Cp2020HealthStrategy;
import app.charka.system.cp2020.Cp2020ItemTypes;
import app.charka.system.cp2020.Cp2020StatsStrategy;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Transactional
@DisplayName("NPC: на настоящих сервисах")
class NpcServiceTest {

    @Autowired private NpcService npcService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private CharacterService characterService;
    @Autowired private EntityManager entityManager;
    @Autowired private MockMvc mockMvc;

    @MockitoBean(answers = Answers.CALLS_REAL_METHODS) private DiceRoller dice;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        when(dice.roll(anyInt())).thenReturn(4);
        campaign = campaign("CP2020");
    }

    private Campaign campaign(String system) {
        Campaign created = new Campaign();
        created.setName("Тест");
        created.setSystem(system);
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        return campaignRepository.save(created);
    }

    private Character reload(Character character) {
        entityManager.flush();
        entityManager.clear();
        return characterRepository.findById(character.getId()).orElseThrow();
    }

    private static Counter counter(Character character, String type) {
        return character.getCounters().stream().filter(c -> type.equals(c.getType())).findFirst().orElseThrow();
    }

    private static List<Item> items(Character character) {
        return character.getInventories().stream().flatMap(inventory -> inventory.getContent().stream()).toList();
    }

    @Test
    @DisplayName("По шаблону: роль, навыки, статы, надетая броня и заряженное оружие")
    void fromTemplate() {
        Character npc = reload(npcService.create(campaign.getId(), "booster", "", 1, false).get(0));

        assertThat(npc.isNpc()).isTrue();
        assertThat(npc.getName()).isEqualTo("Бустер");
        assertThat(npc.getRoles()).containsExactly("solo");
        assertThat(npc.getSkills()).hasSize(10);
        assertThat(counter(npc, Cp2020StatsStrategy.TYPE).getAttributes()).containsEntry("ref", 8);
        assertThat(items(npc)).hasSize(2);
        Item armor = items(npc).stream().filter(item -> Cp2020ItemTypes.ARMOR.equals(item.getType())).findFirst().orElseThrow();
        assertThat(armor.getProperties()).containsKey(Cp2020ItemTypes.WORN);
    }

    @Test
    @DisplayName("Несколько сразу получают номера, повторное создание их не занимает")
    void numbered() {
        List<Character> first = npcService.create(campaign.getId(), "bandit", "", 2, false);
        entityManager.flush();
        entityManager.clear();
        List<Character> second = npcService.create(campaign.getId(), "bandit", "", 1, false);
        entityManager.flush();
        entityManager.clear();
        List<Character> third = npcService.create(campaign.getId(), "bandit", "", 1, false);

        assertThat(first).extracting(Character::getName).containsExactly("Бандит 1", "Бандит 2");
        assertThat(second.get(0).getName()).isEqualTo("Бандит");
        assertThat(third.get(0).getName()).isEqualTo("Бандит 3");
    }

    @Test
    @DisplayName("Пустой NPC есть в любой системе, шаблонов у GENERIC нет")
    void generic() {
        Campaign generic = campaign("GENERIC");

        assertThat(npcService.create(generic.getId(), null, "Страж", 1, false).get(0).isNpc()).isTrue();
        assertThatThrownBy(() -> npcService.create(generic.getId(), "booster", "", 1, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Копия: те же статы, навыки и снаряжение, без ран")
    void copy() {
        Character source = reload(npcService.create(campaign.getId(), "cop", "Коп Джонс", 1, false).get(0));
        counter(source, Cp2020HealthStrategy.TYPE).setValue(12);
        entityManager.flush();

        List<Character> copies = npcService.copy(source.getId(), 2);
        Character copy = reload(copies.get(1));

        assertThat(copies).extracting(Character::getName).containsExactly("Коп Джонс 2", "Коп Джонс 3");
        assertThat(copy.isNpc()).isTrue();
        assertThat(copy.getRoles()).containsExactly("cop");
        assertThat(copy.getSkills()).hasSize(10);
        assertThat(counter(copy, Cp2020StatsStrategy.TYPE).getAttributes()).containsEntry("ref", 8);
        assertThat(counter(copy, Cp2020HealthStrategy.TYPE).getValue()).isZero();
        assertThat(copy.getCounters()).hasSize(4);
        assertThat(items(copy)).hasSize(2);
        assertThat(items(copy)).allSatisfy(item -> assertThat(item.getProperties()).doesNotContainKey(Cp2020ItemTypes.JAMMED));
        Character original = characterRepository.findById(source.getId()).orElseThrow();
        assertThat(items(original)).hasSize(2);
    }

    @Test
    @DisplayName("Копия получает снаряжение как новое: целая броня, полный магазин, оружие не заклинило")
    void copyGearIsFresh() {
        Character source = reload(npcService.create(campaign.getId(), "cop", "Коп", 1, false).get(0));
        Item weapon = items(source).stream().filter(item -> Cp2020ItemTypes.WEAPON.equals(item.getType())).findFirst().orElseThrow();
        Item armor = items(source).stream().filter(item -> Cp2020ItemTypes.ARMOR.equals(item.getType())).findFirst().orElseThrow();
        weapon.getProperties().put(Cp2020ItemTypes.LOADED, "0");
        weapon.getProperties().put(Cp2020ItemTypes.JAMMED, "1");
        armor.getProperties().put("wear_TORSO", "5");
        entityManager.flush();

        Character copy = reload(npcService.copy(source.getId(), 1).get(0));

        Item copiedWeapon = items(copy).stream().filter(item -> Cp2020ItemTypes.WEAPON.equals(item.getType())).findFirst().orElseThrow();
        Item copiedArmor = items(copy).stream().filter(item -> Cp2020ItemTypes.ARMOR.equals(item.getType())).findFirst().orElseThrow();
        assertThat(copiedWeapon.getProperties()).containsEntry(Cp2020ItemTypes.LOADED, copiedWeapon.getProperties().get(Cp2020ItemTypes.SHOTS))
                .doesNotContainKey(Cp2020ItemTypes.JAMMED);
        assertThat(copiedArmor.getProperties()).containsKey(Cp2020ItemTypes.WORN).doesNotContainKey("wear_TORSO");
    }

    @Test
    @DisplayName("Персонажа игрока нельзя скопировать и удалить как NPC")
    void playersProtected() {
        Character player = new Character();
        player.setName("Игрок");
        Character saved = characterService.createInCampaign(campaign.getId(), player);

        assertThatThrownBy(() -> npcService.copy(saved.getId(), 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> npcService.delete(saved.getId())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Удаление убирает NPC вместе с вещами")
    void delete() {
        Character npc = reload(npcService.create(campaign.getId(), "bandit", "", 1, false).get(0));
        Long id = npc.getId();

        npcService.delete(id);
        entityManager.flush();
        entityManager.clear();

        assertThat(characterRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("Число за раз ограничено")
    void limit() {
        assertThatThrownBy(() -> npcService.create(campaign.getId(), "bandit", "", 21, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> npcService.create(campaign.getId(), "bandit", "", 0, false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Страницы с настоящими шаблонами: кампания делит игроков и NPC, лист NPC и выбор цели")
    void pages() throws Exception {
        Character player = new Character();
        player.setName("Игрок");
        Character saved = characterService.createInCampaign(campaign.getId(), player);
        Character npc = npcService.create(campaign.getId(), "bandit", "", 1, false).get(0);
        entityManager.flush();
        entityManager.clear();

        String campaignPage = mockMvc.perform(get("/campaign/" + campaign.getId())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String npcSheet = mockMvc.perform(get("/character/" + npc.getId())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String playerSkills = mockMvc.perform(get("/character/" + saved.getId() + "/skills")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String home = mockMvc.perform(get("/")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(campaignPage).contains("＋ NPC", "Корп-охрана", "＋ Копия", "Цел", "SP торса");
        assertThat(npcSheet).contains("Лист NPC", "<span class=\"target-group\">Игроки</span>");
        assertThat(playerSkills).contains("<optgroup label=\"NPC\"");
        assertThat(home).contains("Игрок").doesNotContain("Бандит");
    }
}
