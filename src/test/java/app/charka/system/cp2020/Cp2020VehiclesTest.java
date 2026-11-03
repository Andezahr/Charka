package app.charka.system.cp2020;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.service.CharacterService;
import app.charka.service.ItemService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/** REF 5 по умолчанию, навыка вождения нет */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Транспорт CP2020 (P-06, с. 113)")
class Cp2020VehiclesTest {

    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private CharacterService characterService;
    @Autowired private ItemService itemService;
    @Autowired private EntityManager entityManager;

    @MockitoBean(answers = Answers.CALLS_REAL_METHODS) private DiceRoller dice;

    private Character driver;
    private Long inventoryId;

    @BeforeEach
    void setUp() {
        Campaign created = new Campaign();
        created.setName("Тест");
        created.setSystem("CP2020");
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        Campaign campaign = campaignRepository.save(created);
        Character character = new Character();
        character.setName("Водила");
        driver = characterService.createInCampaign(campaign.getId(), character);
        inventoryId = driver.getInventories().getFirst().getId();
        flush();
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }

    private Item car(String kind, int sdp, int sp) {
        Item item = new Item();
        item.setName("Машина");
        item.setQuantity(1);
        Item created = itemService.create(inventoryId, item, Cp2020Vehicles.TYPE,
                Map.of(Cp2020Vehicles.KIND, kind, Cp2020Vehicles.SDP, String.valueOf(sdp), Cp2020Vehicles.SP, String.valueOf(sp)));
        flush();
        return created;
    }

    private String act(Item item, String action, String source, String amount) {
        String result = itemService.performAction(driver.getId(), item.getId(), action,
                Map.of("source", source, "amount", amount));
        flush();
        return result;
    }

    private Item reload(Item item) {
        return itemService.findById(item.getId());
    }

    @Nested
    @DisplayName("Урон")
    class Damage {

        @Test
        @DisplayName("SP вычитается, остаток в SDP; 0 SDP — разрушена, «починить» возвращает")
        void throughArmor() {
            Item car = car("CAR", 30, 10);

            assertThat(act(car, Cp2020Vehicles.HIT, "", "25")).contains("= 15").contains("SDP 15/30");
            assertThat(act(car, Cp2020Vehicles.HIT, "", "40")).contains("разрушена");
            assertThat(Cp2020Vehicles.damage(reload(car))).isEqualTo(30);

            act(car, Cp2020Vehicles.REPAIR, "", "");
            assertThat(Cp2020Vehicles.damage(reload(car))).isZero();
        }

        @Test
        @DisplayName("Столкновение: 100 км/ч в машину — 5D6 × 3, машине в SDP, пассажирам половина")
        void ram() {
            when(dice.roll(anyInt())).thenReturn(4);
            Item car = car("CAR", 100, 0);

            String result = act(car, Cp2020Vehicles.RAM, "HEAVY", "100");

            assertThat(result).contains("5D6 = 20 × 3 = 60").contains("пассажирам по 30").contains("SDP 40/100");
        }

        @Test
        @DisplayName("Об коробку ×0.5")
        void light() {
            when(dice.roll(anyInt())).thenReturn(3);
            Item car = car("CAR", 100, 0);

            assertThat(act(car, Cp2020Vehicles.RAM, "VERY_LIGHT", "40")).contains("2D6 = 6 × 0.5 = 3");
        }
    }

    @Nested
    @DisplayName("Контроль")
    class Control {

        @Test
        @DisplayName("REF + навык + 1D10 + вид машины: спорткар на 9 удерживает простой манёвр")
        void success() {
            when(dice.roll(anyInt())).thenReturn(8);
            Item car = car("SPORTSCAR", 30, 0);

            // REF 6 + 0 + 8 + 2 = 16
            assertThat(act(car, Cp2020Vehicles.CONTROL, "15", "1")).contains("16 против 15").contains("удержал");
        }

        @Test
        @DisplayName("Провал на тройной скорости: грузовик переворачивается и получает 5D6")
        void rollover() {
            when(dice.roll(anyInt())).thenReturn(6, 6, 2, 6, 6, 6, 6, 6);
            Item truck = car("TRUCK", 50, 0);

            // REF 6 + 0 + 6 − 4 − 4 = 4 против 20: провал; 1D6 = 6 → переворот, снос 2×10, 5D6 = 30
            String result = act(truck, Cp2020Vehicles.CONTROL, "20", "3");

            assertThat(result).contains("4 против 20").contains("переворот").contains("SDP 20/50");
        }

        @Test
        @DisplayName("Летательный: сваливание вместо заноса")
        void aircraft() {
            when(dice.roll(anyInt())).thenReturn(1, 3, 4);
            Item av = car("AV4", 50, 10);

            assertThat(act(av, Cp2020Vehicles.CONTROL, "25", "1")).contains("сваливание").contains("200 футов");
        }
    }
}
