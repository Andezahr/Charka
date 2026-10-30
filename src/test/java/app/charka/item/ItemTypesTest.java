package app.charka.item;

import app.charka.exception.NotFoundException;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Inventory;
import app.charka.model.Item;
import app.charka.repository.InventoryRepository;
import app.charka.repository.ItemRepository;
import app.charka.service.ItemService;
import app.charka.system.GameSystemRegistry;
import app.charka.system.GenericSystem;
import app.charka.system.cp2020.Cp2020ItemTypes;
import app.charka.system.cp2020.Cp2020System;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Типы предметов")
class ItemTypesTest {

    private static final ItemTypeDefinition TEST_TYPE = new ItemTypeDefinition("TEST", "Тест", List.of(
            ItemField.number("level", "Уровень", "ур. ", "", 0, 5),
            ItemField.text("note", "Заметка"),
            ItemField.dice("loss", "Потеря"),
            ItemField.choice("kind", "Вид", true, new ItemField.Option("A", "Первый"), new ItemField.Option("B", "Второй")),
            ItemField.flag("heavy", "Тяжёлый", "тяж.")));

    @Nested
    @DisplayName("Проверка значений")
    class NormalizeTests {

        @Test
        @DisplayName("приводит значения к хранимому виду, пустые и незнакомые не сохраняет")
        void normalizes() {
            Map<String, String> result = TEST_TYPE.normalize(Map.of(
                    "level", " 3 ", "note", " ", "loss", "2D6+1", "kind", "B", "heavy", "on", "alien", "x"));

            assertThat(result).containsOnly(
                    Map.entry("level", "3"), Map.entry("loss", "2d6+1"), Map.entry("kind", "B"), Map.entry("heavy", "1"));
        }

        @ParameterizedTest(name = "{0}={1}")
        @CsvSource({"level, abc", "level, 6", "level, -1", "loss, много", "kind, C"})
        @DisplayName("значение, которое не подходит полю, — ошибка с названием поля")
        void rejects(String key, String value) {
            Map<String, String> input = new HashMap<>(Map.of("kind", "A"));
            input.put(key, value);

            assertThatThrownBy(() -> TEST_TYPE.normalize(input))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Поле");
        }

        @Test
        @DisplayName("обязательное поле нельзя оставить пустым; слишком длинный текст — ошибка")
        void requiredAndLength() {
            assertThatThrownBy(() -> TEST_TYPE.normalize(Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Вид");
            assertThatThrownBy(() -> TEST_TYPE.normalize(Map.of("kind", "A", "note", "x".repeat(256))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Оружие CP2020")
    class WeaponTests {

        @Test
        @DisplayName("строка предмета — как код оружия в книге: Minami 10 • SMG • 0 • J • E • 2D6+3/10mm • 40 • 20 • VR")
        void bookLine() {
            Map<String, String> properties = Cp2020ItemTypes.WEAPON_TYPE.normalize(Map.of(
                    "category", "SMG", "accuracy", "0", "concealability", "J", "availability", "E", "damage", "2D6+3",
                    "caliber", "10mm", "shots", "40", "rof", "20", "reliability", "VR", "range", "200"));

            assertThat(Cp2020ItemTypes.WEAPON_TYPE.describe(properties)).extracting(ItemTypeDefinition.Line::text)
                    .containsExactly("SMG", "WA 0", "J", "E", "2D6+3", "10mm", "магазин 40", "ROF 20", "VR", "200 м");
            assertThat(Cp2020ItemTypes.WEAPON_TYPE.describe(properties).getFirst().title())
                    .isEqualTo("Тип: Пистолет-пулемёт");
        }

        @Test
        @DisplayName("урон из книги, который не кубики, принимается; точность только от −3 до +3")
        void damageAndAccuracy() {
            assertThat(Cp2020ItemTypes.WEAPON_TYPE.normalize(Map.of("category", "EX", "damage", "1-5D6")))
                    .containsEntry("damage", "1-5D6");
            assertThatThrownBy(() -> Cp2020ItemTypes.WEAPON_TYPE.normalize(Map.of("category", "P", "accuracy", "4")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("бронебойность показывается отметкой AP")
        void armorPiercing() {
            Map<String, String> properties = Cp2020ItemTypes.WEAPON_TYPE.normalize(Map.of("category", "P", "armorPiercing", "1"));

            assertThat(Cp2020ItemTypes.WEAPON_TYPE.describe(properties)).extracting(ItemTypeDefinition.Line::text)
                    .containsExactly("P", "AP");
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    @DisplayName("Сохранение предмета")
    class ServiceTests {

        @Mock private ItemRepository itemRepository;
        @Mock private InventoryRepository inventoryRepository;

        private ItemService itemService;
        private Inventory inventory;

        @BeforeEach
        void setUp() {
            GameSystemRegistry registry = new GameSystemRegistry(List.of(new GenericSystem(), new Cp2020System(null)));
            itemService = new ItemService(itemRepository, inventoryRepository, registry);
            inventory = inventoryIn(Cp2020System.CODE);
            when(inventoryRepository.findById(1L)).thenReturn(Optional.of(inventory));
            when(itemRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        }

        private Inventory inventoryIn(String system) {
            Campaign campaign = new Campaign();
            campaign.setSystem(system);
            Character character = new Character();
            character.setCampaign(campaign);
            Inventory result = new Inventory();
            result.setCharacter(character);
            return result;
        }

        @Test
        @DisplayName("создание: тип и проверенные значения сохраняются")
        void create() {
            Item item = itemService.create(1L, new Item(), Cp2020ItemTypes.WEAPON, Map.of("category", "RIF", "alien", "x"));

            assertThat(item.getType()).isEqualTo(Cp2020ItemTypes.WEAPON);
            assertThat(item.getProperties()).containsOnly(Map.entry("category", "RIF"));
            assertThat(item.getInventory()).isSameAs(inventory);
        }

        @Test
        @DisplayName("тип другой системы и неверное значение — ошибка, ничего не сохраняется")
        void rejected() {
            Inventory generic = inventoryIn(GenericSystem.CODE);
            when(inventoryRepository.findById(2L)).thenReturn(Optional.of(generic));

            assertThatThrownBy(() -> itemService.create(2L, new Item(), Cp2020ItemTypes.WEAPON, Map.of("category", "RIF")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("недоступен");
            assertThatThrownBy(() -> itemService.create(1L, new Item(), Cp2020ItemTypes.WEAPON, Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(itemRepository, never()).save(any());
        }

        @Test
        @DisplayName("обычный предмет доступен в любой системе")
        void genericEverywhere() {
            Inventory generic = inventoryIn(GenericSystem.CODE);
            when(inventoryRepository.findById(2L)).thenReturn(Optional.of(generic));

            Item item = itemService.create(2L, new Item(), ItemTypeDefinition.GENERIC_CODE, Map.of());

            assertThat(item.getType()).isEqualTo(ItemTypeDefinition.GENERIC_CODE);
            assertThat(item.getProperties()).isEmpty();
        }

        @Test
        @DisplayName("смена типа: значения прежнего типа не остаются")
        void changeType() {
            Item existing = new Item();
            existing.setInventory(inventory);
            existing.setType(Cp2020ItemTypes.WEAPON);
            existing.getProperties().put("category", "RIF");
            when(itemRepository.findById(5L)).thenReturn(Optional.of(existing));

            itemService.update(5L, "Лом", 1, 0, null, ItemTypeDefinition.GENERIC_CODE, Map.of());

            assertThat(existing.getName()).isEqualTo("Лом");
            assertThat(existing.getType()).isEqualTo(ItemTypeDefinition.GENERIC_CODE);
            assertThat(existing.getProperties()).isEmpty();
        }

        @Test
        @DisplayName("неизвестный инвентарь — 404-исключение")
        void unknownInventory() {
            when(inventoryRepository.findById(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> itemService.create(9L, new Item(), ItemTypeDefinition.GENERIC_CODE, Map.of()))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Test
    @DisplayName("JSON-экспорт содержит тип и свойства предмета")
    void exported() throws Exception {
        Item item = new Item();
        item.setType(Cp2020ItemTypes.WEAPON);
        item.getProperties().put("category", "RIF");

        String json = new ObjectMapper().writeValueAsString(item);

        assertThat(json).contains("\"type\":\"CP2020_WEAPON\"", "\"properties\":{\"category\":\"RIF\"}");
    }
}
