package app.charka.system.cp2020;

import app.charka.item.ItemAction;
import app.charka.item.ItemField;
import app.charka.model.Character;
import app.charka.model.Inventory;
import app.charka.model.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Магазин оружия CP2020")
class Cp2020WeaponActionsTest {

    private Character character;
    private Inventory backpack;
    private Inventory stash;
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        character = new Character();
        backpack = inventory("Рюкзак");
        stash = inventory("Схрон");
    }

    private Inventory inventory(String name) {
        Inventory inventory = new Inventory();
        inventory.setName(name);
        inventory.setContent(new ArrayList<>());
        inventory.setCharacter(character);
        character.getInventories().add(inventory);
        return inventory;
    }

    private Item weapon(int shots, int loaded, int rof, String caliber) {
        Item item = new Item();
        item.setId(nextId++);
        item.setName("Minami");
        item.setQuantity(1);
        item.setType(Cp2020ItemTypes.WEAPON);
        item.getProperties().put("category", "SMG");
        item.getProperties().put("shots", String.valueOf(shots));
        item.getProperties().put("ammo", String.valueOf(loaded));
        item.getProperties().put("rof", String.valueOf(rof));
        if (caliber != null) {
            item.getProperties().put("caliber", caliber);
        }
        item.setInventory(backpack);
        backpack.getContent().add(item);
        return item;
    }

    private Item stack(Inventory inventory, String caliber, int rounds, boolean armorPiercing) {
        Item item = new Item();
        item.setId(nextId++);
        item.setName("Патроны");
        item.setQuantity(rounds);
        item.setType(Cp2020ItemTypes.AMMO);
        item.getProperties().put("caliber", caliber);
        if (armorPiercing) {
            item.getProperties().put("armorPiercing", "1");
        }
        item.setInventory(inventory);
        inventory.getContent().add(item);
        return item;
    }

    private ItemAction action(Item weapon, String code) {
        return Cp2020WeaponActions.actions(weapon).stream().filter(a -> a.code().equals(code)).findFirst().orElseThrow();
    }

    private String reload(Item weapon, Item stack) {
        return Cp2020WeaponActions.perform(weapon, Cp2020WeaponActions.RELOAD, Map.of("source", String.valueOf(stack.getId())));
    }

    private static int loaded(Item weapon) {
        return Integer.parseInt(weapon.getProperties().get("ammo"));
    }

    @Nested
    @DisplayName("Какие действия доступны")
    class Available {

        @Test
        @DisplayName("без магазина действий нет: холодное оружие, обычный предмет")
        void noMagazine() {
            Item knife = new Item();
            knife.setType(Cp2020ItemTypes.WEAPON);
            knife.getProperties().put("category", "MELEE");
            Item rope = new Item();

            assertThat(Cp2020WeaponActions.actions(knife)).isEmpty();
            assertThat(Cp2020WeaponActions.actions(rope)).isEmpty();
        }

        @Test
        @DisplayName("очередь есть только при ROF больше 1; пистолет с ROF 1 стреляет одиночными")
        void burstNeedsRof() {
            assertThat(Cp2020WeaponActions.actions(weapon(8, 8, 1, "12mm"))).extracting(ItemAction::code)
                    .containsExactly("FIRE", "RELOAD");
            assertThat(Cp2020WeaponActions.actions(weapon(40, 40, 20, "10mm"))).extracting(ItemAction::code)
                    .containsExactly("FIRE", "BURST", "RELOAD");
        }

        @Test
        @DisplayName("очередь: по умолчанию 3, не больше ROF и заряда")
        void burstLimits() {
            ItemAction full = action(weapon(40, 40, 20, "10mm"), "BURST");
            ItemAction nearlyEmpty = action(weapon(40, 2, 20, "10mm"), "BURST");

            assertThat(full.amount()).isEqualTo(3);
            assertThat(full.maxAmount()).isEqualTo(20);
            assertThat(nearlyEmpty.amount()).isEqualTo(2);
            assertThat(nearlyEmpty.maxAmount()).isEqualTo(2);
        }

        @Test
        @DisplayName("пустой магазин: стрелять нельзя, причина названа")
        void empty() {
            Item gun = weapon(8, 0, 2, "12mm");

            assertThat(action(gun, "FIRE").unavailable()).isEqualTo("Магазин пуст");
            assertThat(action(gun, "BURST").unavailable()).isEqualTo("Магазин пуст");
        }

        @Test
        @DisplayName("перезарядка предлагает только тот же калибр из всех инвентарей персонажа; сначала запас рядом с оружием")
        void reloadSources() {
            Item gun = weapon(10, 5, 2, "9mm");
            Item far = stack(stash, "9mm", 50, false);
            Item near = stack(backpack, "9MM", 20, false);
            stack(backpack, "10mm", 50, false);
            Item otherOwner = stack(inventory("Чужой"), "9mm", 50, false);
            otherOwner.getInventory().setCharacter(new Character());
            character.getInventories().remove(otherOwner.getInventory());

            ItemAction reload = action(gun, "RELOAD");

            assertThat(reload.unavailable()).isNull();
            assertThat(reload.sources()).extracting(ItemField.Option::value)
                    .containsExactly(String.valueOf(near.getId()), String.valueOf(far.getId()));
            assertThat(reload.sources().getFirst().label()).isEqualTo("9MM × 20 (Рюкзак)");
        }

        @Test
        @DisplayName("перезарядить нечем или незачем — причина названа")
        void reloadUnavailable() {
            assertThat(action(weapon(10, 5, 2, "9mm"), "RELOAD").unavailable()).contains("Нет патронов «9mm»");
            assertThat(action(weapon(10, 5, 2, null), "RELOAD").unavailable()).contains("не указан патрон");

            Item full = weapon(10, 10, 2, "5mm");
            stack(backpack, "5mm", 50, false);
            assertThat(action(full, "RELOAD").unavailable()).isEqualTo("Магазин полон");
        }

        @Test
        @DisplayName("полный магазин можно сменить на патроны другого вида")
        void fullButOtherKind() {
            Item gun = weapon(10, 10, 2, "9mm");
            Item armorPiercing = stack(backpack, "9mm", 50, true);

            assertThat(action(gun, "RELOAD").sources()).extracting(ItemField.Option::value)
                    .containsExactly(String.valueOf(armorPiercing.getId()));
        }
    }

    @Nested
    @DisplayName("Стрельба")
    class Firing {

        @Test
        @DisplayName("выстрел расходует один патрон, очередь — сколько указано")
        void consumes() {
            Item gun = weapon(40, 40, 20, "10mm");

            Cp2020WeaponActions.perform(gun, "FIRE", Map.of());
            assertThat(loaded(gun)).isEqualTo(39);

            String message = Cp2020WeaponActions.perform(gun, "BURST", Map.of("amount", "3"));
            assertThat(loaded(gun)).isEqualTo(36);
            assertThat(message).isEqualTo("Minami: выпущено 3, заряжено 36 из 40");
        }

        @Test
        @DisplayName("нельзя выпустить больше ROF, больше заряда и из пустого магазина")
        void limits() {
            Item gun = weapon(40, 5, 20, "10mm");

            assertThatThrownBy(() -> Cp2020WeaponActions.perform(gun, "BURST", Map.of("amount", "21")))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ROF");
            assertThatThrownBy(() -> Cp2020WeaponActions.perform(gun, "BURST", Map.of("amount", "6")))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("В магазине только 5");
            assertThatThrownBy(() -> Cp2020WeaponActions.perform(gun, "BURST", Map.of("amount", "0")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Cp2020WeaponActions.perform(gun, "BURST", Map.of("amount", "много")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Cp2020WeaponActions.perform(gun, "BURST", Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(loaded(gun)).isEqualTo(5);

            Item empty = weapon(8, 0, 2, "12mm");
            assertThatThrownBy(() -> Cp2020WeaponActions.perform(empty, "FIRE", Map.of()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Магазин пуст");
        }

        @Test
        @DisplayName("последний бронебойный патрон выпущен — отметка AP снимается")
        void lastArmorPiercing() {
            Item gun = weapon(8, 1, 2, "12mm");
            gun.getProperties().put("armorPiercing", "1");

            Cp2020WeaponActions.perform(gun, "FIRE", Map.of());

            assertThat(loaded(gun)).isZero();
            assertThat(gun.getProperties()).doesNotContainKey("armorPiercing");
        }

        @Test
        @DisplayName("неизвестное действие и действие у оружия без магазина — ошибка")
        void unknownAction() {
            Item gun = weapon(8, 8, 2, "12mm");
            Item knife = new Item();
            knife.setType(Cp2020ItemTypes.WEAPON);

            assertThatThrownBy(() -> Cp2020WeaponActions.perform(gun, "JUGGLE", Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Cp2020WeaponActions.perform(knife, "FIRE", Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Перезарядка")
    class Reloading {

        @Test
        @DisplayName("добирает магазин до полного, запас уменьшается")
        void topsUp() {
            Item gun = weapon(10, 4, 2, "9mm");
            Item box = stack(backpack, "9mm", 50, false);

            String message = reload(gun, box);

            assertThat(loaded(gun)).isEqualTo(10);
            assertThat(box.getQuantity()).isEqualTo(44);
            assertThat(message).isEqualTo("Minami: заряжено 10 из 10, из запаса взято 6");
        }

        @Test
        @DisplayName("патронов меньше, чем нужно: заряжает сколько есть, пустая коробка исчезает")
        void lastRounds() {
            Item gun = weapon(10, 4, 2, "9mm");
            Item box = stack(stash, "9mm", 3, false);

            reload(gun, box);

            assertThat(loaded(gun)).isEqualTo(7);
            assertThat(stash.getContent()).isEmpty();
        }

        @Test
        @DisplayName("смена вида: остаток возвращается в такой же запас, в магазине только новые, отметка AP ставится")
        void swapToArmorPiercing() {
            Item gun = weapon(10, 4, 2, "9mm");
            Item plain = stack(backpack, "9mm", 20, false);
            Item armorPiercing = stack(backpack, "9mm", 30, true);

            String message = reload(gun, armorPiercing);

            assertThat(loaded(gun)).isEqualTo(10);
            assertThat(gun.getProperties()).containsEntry("armorPiercing", "1");
            assertThat(plain.getQuantity()).isEqualTo(24);
            assertThat(armorPiercing.getQuantity()).isEqualTo(20);
            assertThat(message).contains("AP", "взято 10", "возвращено 4");
        }

        @Test
        @DisplayName("смена вида без такого запаса: остаток становится новым запасом рядом с оружием, AP снимается")
        void swapToPlainCreatesStack() {
            Item gun = weapon(10, 4, 2, "9mm");
            gun.getProperties().put("armorPiercing", "1");
            Item plain = stack(stash, "9mm", 20, false);

            reload(gun, plain);

            assertThat(gun.getProperties()).doesNotContainKey("armorPiercing");
            List<Item> returned = backpack.getContent().stream().filter(item -> item != gun).toList();
            assertThat(returned).hasSize(1);
            assertThat(returned.getFirst().getName()).isEqualTo("Патроны 9mm AP");
            assertThat(returned.getFirst().getQuantity()).isEqualTo(4);
            assertThat(returned.getFirst().getType()).isEqualTo(Cp2020ItemTypes.AMMO);
            assertThat(returned.getFirst().getProperties()).containsEntry("caliber", "9mm").containsEntry("armorPiercing", "1");
        }

        @Test
        @DisplayName("чужой калибр, чужой запас и полный магазин — ошибка, ничего не меняется")
        void rejected() {
            Item gun = weapon(10, 10, 2, "9mm");
            Item wrong = stack(backpack, "10mm", 50, false);
            Item same = stack(backpack, "9mm", 50, false);

            assertThatThrownBy(() -> reload(gun, wrong)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Cp2020WeaponActions.perform(gun, "RELOAD", Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> reload(gun, same))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Магазин полон");
            assertThat(same.getQuantity()).isEqualTo(50);
            assertThat(wrong.getQuantity()).isEqualTo(50);
        }
    }
}
