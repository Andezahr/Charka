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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Боепитание CP2020")
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

    private Item add(Inventory inventory, Item item) {
        item.setId(nextId++);
        item.setInventory(inventory);
        inventory.getContent().add(item);
        return item;
    }

    private Item weapon(int shots, Integer loaded, int rof, String caliber) {
        Item item = new Item();
        item.setName("Minami");
        item.setQuantity(1);
        item.setType(Cp2020ItemTypes.WEAPON);
        item.getProperties().put("category", "SMG");
        item.getProperties().put("shots", String.valueOf(shots));
        if (loaded != null) {
            item.getProperties().put("ammo", String.valueOf(loaded));
        }
        item.getProperties().put("rof", String.valueOf(rof));
        if (caliber != null) {
            item.getProperties().put("caliber", caliber);
        }
        return add(backpack, item);
    }

    private Item revolver(int shots, int loaded, String caliber) {
        Item item = weapon(shots, loaded, 1, caliber);
        item.getProperties().put("directLoad", "1");
        return item;
    }

    private Item stack(Inventory inventory, String caliber, int rounds, boolean armorPiercing) {
        Item item = new Item();
        item.setName("Патроны");
        item.setQuantity(rounds);
        item.setType(Cp2020ItemTypes.AMMO);
        item.getProperties().put("caliber", caliber);
        if (armorPiercing) {
            item.getProperties().put("armorPiercing", "1");
        }
        return add(inventory, item);
    }

    private Item magazine(Inventory inventory, String caliber, int capacity, int loaded, boolean armorPiercing) {
        Item item = Cp2020Magazines.newMagazine(caliber, capacity, loaded, armorPiercing);
        return add(inventory, item);
    }

    private ItemAction action(Item item, String code) {
        return Cp2020WeaponActions.actions(item).stream().filter(a -> a.code().equals(code)).findFirst().orElseThrow();
    }

    private static List<String> codes(Item item) {
        return Cp2020WeaponActions.actions(item).stream().map(ItemAction::code).toList();
    }

    private String perform(Item item, String code, Item source) {
        return Cp2020WeaponActions.perform(item, code, Map.of("source", String.valueOf(source.getId())));
    }

    private static int loaded(Item item) {
        return Integer.parseInt(item.getProperties().get("ammo"));
    }

    private List<Item> magazinesIn(Inventory inventory) {
        return inventory.getContent().stream().filter(item -> Cp2020ItemTypes.MAGAZINE.equals(item.getType())).toList();
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

            assertThat(Cp2020WeaponActions.actions(knife)).isEmpty();
            assertThat(Cp2020WeaponActions.actions(new Item())).isEmpty();
        }

        @Test
        @DisplayName("очередь есть только при ROF больше 1; извлечь можно, пока магазин в оружии")
        void weaponActions() {
            assertThat(codes(weapon(8, 8, 1, "12mm"))).containsExactly("FIRE", "RELOAD", "EJECT", "ADD_MAGAZINE");
            assertThat(codes(weapon(40, 40, 20, "10mm"))).containsExactly("FIRE", "BURST", "RELOAD", "EJECT", "ADD_MAGAZINE");
            assertThat(codes(weapon(40, null, 20, "10mm"))).containsExactly("FIRE", "BURST", "RELOAD", "ADD_MAGAZINE");
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
        @DisplayName("почему нельзя стрелять: магазин пуст, магазина нет, оружие не заряжено")
        void emptyReasons() {
            assertThat(action(weapon(8, 0, 2, "12mm"), "FIRE").unavailable()).isEqualTo("Магазин пуст");
            assertThat(action(weapon(8, null, 2, "12mm"), "FIRE").unavailable()).isEqualTo("В оружии нет магазина");
            assertThat(action(revolver(6, 0, ".44"), "FIRE").unavailable()).isEqualTo("Оружие не заряжено");
        }

        @Test
        @DisplayName("перезарядка предлагает снаряжённые магазины того же патрона и подходящего размера, самые полные первыми")
        void magazineSources() {
            Item gun = weapon(10, 5, 2, "9mm");
            Item half = magazine(backpack, "9mm", 10, 5, false);
            Item full = magazine(stash, "9MM", 10, 10, false);
            Item drum = magazine(backpack, "9mm", 50, 7, true);
            magazine(backpack, "9mm", 10, 0, false);
            magazine(backpack, "10mm", 10, 10, false);
            magazine(backpack, "9mm", 8, 8, false);
            magazine(backpack, "9mm", 51, 51, false);

            ItemAction reload = action(gun, "RELOAD");

            assertThat(reload.unavailable()).isNull();
            assertThat(reload.sources()).extracting(ItemField.Option::value)
                    .containsExactly(String.valueOf(full.getId()), String.valueOf(drum.getId()), String.valueOf(half.getId()));
            assertThat(reload.sources()).extracting(ItemField.Option::label)
                    .containsExactly("10 из 10 (Схрон)", "7 из 50 AP (Рюкзак)", "5 из 10 (Рюкзак)");
        }

        @Test
        @DisplayName("нечем перезарядить — причина названа с нужным размером")
        void noMagazines() {
            Item gun = weapon(10, 5, 2, "9mm");
            stack(backpack, "9mm", 50, false);

            assertThat(action(gun, "RELOAD").unavailable()).contains("Нет снаряжённых магазинов «9mm» на 10–50");
        }

        @Test
        @DisplayName("оружие без магазина предлагает патроны; без патрона в свойствах — объяснение")
        void directSources() {
            Item revolver = revolver(6, 2, ".44");
            Item box = stack(backpack, ".44", 20, false);
            stack(backpack, ".45", 20, false);
            Item laser = weapon(10, 5, 2, null);

            assertThat(action(revolver, "RELOAD").sources()).extracting(ItemField.Option::value)
                    .containsExactly(String.valueOf(box.getId()));
            assertThat(action(revolver, "RELOAD").sources().getFirst().label()).isEqualTo(".44 × 20 (Рюкзак)");
            assertThat(codes(revolver)).containsExactly("FIRE", "RELOAD");
            assertThat(action(laser, "RELOAD").unavailable()).contains("не указан патрон");
            assertThat(codes(laser)).containsExactly("FIRE", "BURST", "RELOAD");
        }

        @Test
        @DisplayName("магазин: снарядить из запаса того же калибра, разрядить — только если есть что")
        void magazineActions() {
            Item empty = magazine(backpack, "9mm", 10, 0, false);
            Item loaded = magazine(backpack, "9mm", 10, 4, false);
            Item box = stack(stash, "9mm", 50, false);
            stack(backpack, "10mm", 50, false);

            assertThat(codes(empty)).containsExactly("LOAD");
            assertThat(codes(loaded)).containsExactly("LOAD", "UNLOAD");
            assertThat(action(empty, "LOAD").sources()).extracting(ItemField.Option::value)
                    .containsExactly(String.valueOf(box.getId()));
        }

        @Test
        @DisplayName("полный магазин: тем же видом снаряжать незачем, другим можно")
        void fullMagazine() {
            Item full = magazine(backpack, "9mm", 10, 10, false);
            stack(backpack, "9mm", 50, false);
            assertThat(action(full, "LOAD").unavailable()).isEqualTo("Заряжено полностью");

            Item armorPiercing = stack(backpack, "9mm", 50, true);
            assertThat(action(full, "LOAD").sources()).extracting(ItemField.Option::value)
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
        @DisplayName("нельзя выпустить больше ROF, больше заряда и из пустого или отсутствующего магазина")
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
            Item without = weapon(8, null, 2, "12mm");
            assertThatThrownBy(() -> Cp2020WeaponActions.perform(without, "FIRE", Map.of()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("В оружии нет магазина");
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
        @DisplayName("неизвестное действие и действие не того предмета — ошибка")
        void unknownAction() {
            Item gun = weapon(8, 8, 2, "12mm");
            Item mag = magazine(backpack, "12mm", 8, 8, false);
            Item knife = new Item();
            knife.setType(Cp2020ItemTypes.WEAPON);

            assertThatThrownBy(() -> Cp2020WeaponActions.perform(gun, "JUGGLE", Map.of())).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Cp2020WeaponActions.perform(knife, "FIRE", Map.of())).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Cp2020WeaponActions.perform(mag, "FIRE", Map.of())).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Cp2020WeaponActions.perform(gun, "UNLOAD", Map.of())).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Смена магазина в оружии")
    class ChangingMagazine {

        @Test
        @DisplayName("новый магазин уходит из инвентаря в оружие, прежний с остатком — в инвентарь рядом с оружием")
        void swaps() {
            Item gun = weapon(10, 4, 2, "9mm");
            Item fresh = magazine(stash, "9mm", 10, 10, false);

            String message = perform(gun, "RELOAD", fresh);

            assertThat(loaded(gun)).isEqualTo(10);
            assertThat(stash.getContent()).isEmpty();
            List<Item> aside = magazinesIn(backpack);
            assertThat(aside).hasSize(1);
            assertThat(aside.getFirst().getName()).isEqualTo("Магазин 9mm на 10");
            assertThat(aside.getFirst().getProperties()).containsEntry("ammo", "4").containsEntry("shots", "10").containsEntry("caliber", "9mm");
            assertThat(message).isEqualTo("Minami: заряжено 10 из 10, прежний магазин с 4 патр. убран в инвентарь");
        }

        @Test
        @DisplayName("вид патронов берётся из магазина: AP появляется и снимается, старый AP уходит с прежним магазином")
        void armorPiercingFollowsMagazine() {
            Item gun = weapon(10, 4, 2, "9mm");
            Item armorPiercing = magazine(backpack, "9mm", 10, 10, true);

            perform(gun, "RELOAD", armorPiercing);
            assertThat(gun.getProperties()).containsEntry("armorPiercing", "1");

            Item plain = magazine(backpack, "9mm", 10, 10, false);
            perform(gun, "RELOAD", plain);

            assertThat(gun.getProperties()).doesNotContainKey("armorPiercing");
            assertThat(magazinesIn(backpack)).filteredOn(mag -> mag.getProperties().containsKey("armorPiercing"))
                    .hasSize(1).first().satisfies(mag -> assertThat(loaded(mag)).isEqualTo(10));
        }

        @Test
        @DisplayName("в оружии без магазина прежнего нет: новых магазинов не появляется")
        void noPrevious() {
            Item gun = weapon(10, null, 2, "9mm");
            Item fresh = magazine(backpack, "9mm", 10, 6, false);

            String message = perform(gun, "RELOAD", fresh);

            assertThat(loaded(gun)).isEqualTo(6);
            assertThat(magazinesIn(backpack)).isEmpty();
            assertThat(message).isEqualTo("Minami: заряжено 6 из 10");
        }

        @Test
        @DisplayName("пустой, чужого калибра и слишком большой магазин не встают, ничего не меняется")
        void rejected() {
            Item gun = weapon(10, 4, 2, "9mm");
            Item empty = magazine(backpack, "9mm", 10, 0, false);
            Item wrong = magazine(backpack, "10mm", 10, 10, false);
            Item huge = magazine(backpack, "9mm", 51, 51, false);
            Item small = magazine(backpack, "9mm", 8, 8, false);

            assertThatThrownBy(() -> perform(gun, "RELOAD", empty)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("пуст");
            assertThatThrownBy(() -> perform(gun, "RELOAD", wrong)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> perform(gun, "RELOAD", huge)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> perform(gun, "RELOAD", small)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Cp2020WeaponActions.perform(gun, "RELOAD", Map.of())).isInstanceOf(IllegalArgumentException.class);
            assertThat(loaded(gun)).isEqualTo(4);
            assertThat(magazinesIn(backpack)).hasSize(4);
        }

        @Test
        @DisplayName("＋ Магазин: пустой стандартный магазин этого патрона рядом с оружием, он подходит к нему и снаряжается")
        void addsStandardMagazine() {
            Item gun = weapon(40, 10, 20, "10mm");
            Item box = stack(stash, "10mm", 50, false);

            String message = Cp2020WeaponActions.perform(gun, "ADD_MAGAZINE", Map.of());

            assertThat(message).isEqualTo("Minami: в инвентарь добавлен пустой магазин на 40");
            assertThat(loaded(gun)).isEqualTo(10);
            List<Item> added = magazinesIn(backpack);
            assertThat(added).hasSize(1);
            Item mag = added.getFirst();
            assertThat(mag.getName()).isEqualTo("Магазин 10mm на 40");
            assertThat(mag.getQuantity()).isEqualTo(1);
            assertThat(mag.getCost()).isEqualTo(20);
            assertThat(mag.getDescription()).contains("Хоумбрю");
            assertThat(mag.getProperties()).containsEntry("ammo", "0").containsEntry("shots", "40").containsEntry("caliber", "10mm");

            mag.setId(nextId++);
            perform(mag, "LOAD", box);
            assertThat(action(gun, "RELOAD").sources()).extracting(ItemField.Option::value)
                    .containsExactly(String.valueOf(mag.getId()));
        }

        @ParameterizedTest(name = "{0}, {3}: {4} eb")
        @CsvSource({"Arasaka Minami 10, 10mm, 40, SMG, 20", "UZI, 9mm, 30, SMG, 30",
                "Barrett-Arasaka Light 20mm, 20/9mm, 10, HVY, 20", "Самоделка, 9mm, 35, P, 18"})
        @DisplayName("цена как в справочнике: безгильзовые, старое оружие, тяжёлое; своё оружие — как безгильзовое")
        void standardMagazinePrice(String name, String caliber, int capacity, String category, int cost) {
            assertThat(Cp2020Magazines.standardFor(name, caliber, capacity, category).getCost()).isEqualTo(cost);
        }

        @Test
        @DisplayName("＋ Магазин нет у оружия без магазина и без патрона")
        void noQuickMagazine() {
            assertThat(codes(revolver(6, 6, ".44"))).doesNotContain("ADD_MAGAZINE");
            assertThat(codes(weapon(10, 5, 2, null))).doesNotContain("ADD_MAGAZINE");
        }

        @Test
        @DisplayName("извлечь: магазин с остатком в инвентаре, оружие без магазина и без отметки AP")
        void ejects() {
            Item gun = weapon(10, 4, 2, "9mm");
            gun.getProperties().put("armorPiercing", "1");

            String message = Cp2020WeaponActions.perform(gun, "EJECT", Map.of());

            assertThat(gun.getProperties()).doesNotContainKeys("ammo", "armorPiercing");
            assertThat(magazinesIn(backpack)).hasSize(1).first().satisfies(mag -> {
                assertThat(loaded(mag)).isEqualTo(4);
                assertThat(mag.getProperties()).containsEntry("armorPiercing", "1");
            });
            assertThat(message).isEqualTo("Minami: магазин с 4 патр. убран в инвентарь");
        }
    }

    @Nested
    @DisplayName("Расширенные магазины")
    class ExtendedMagazines {

        @ParameterizedTest(name = "стандартный {0}, магазин {1}: подходит {2}, большой {3}")
        @CsvSource({"10, 9, false, false", "10, 10, true, false", "10, 20, true, false", "10, 21, true, true", "10, 50, true, true", "10, 51, false, true"})
        @DisplayName("подходит от стандартного до пяти стандартных, штрафы — больше двух")
        void limits(int standard, int capacity, boolean fits, boolean oversized) {
            assertThat(Cp2020MagazineRules.fits(standard, capacity)).isEqualTo(fits);
            assertThat(Cp2020MagazineRules.isOversized(standard, capacity)).isEqualTo(oversized);
        }

        @ParameterizedTest(name = "{0} патронов, {1}: {2} eb")
        @CsvSource({"40, CASELESS, 20", "35, CASELESS, 18", "30, CASED, 30", "10, HEAVY, 20"})
        @DisplayName("цена по ставке, дробная — вверх")
        void price(int capacity, Cp2020MagazineRules.Rate rate, int expected) {
            assertThat(Cp2020MagazineRules.price(capacity, rate)).isEqualTo(expected);
        }

        @Test
        @DisplayName("уровень вниз упирается в самый низкий; неизвестный код не меняется")
        void levels() {
            assertThat(Cp2020MagazineRules.lowerReliability("VR")).isEqualTo("ST");
            assertThat(Cp2020MagazineRules.lowerReliability("UR")).isEqualTo("UR");
            assertThat(Cp2020MagazineRules.lowerConcealability("J")).isEqualTo("L");
            assertThat(Cp2020MagazineRules.lowerConcealability("N")).isEqualTo("N");
            assertThat(Cp2020MagazineRules.lowerConcealability("?")).isEqualTo("?");
            assertThat(Cp2020MagazineRules.snapshotModifier("P")).isEqualTo(1);
            assertThat(Cp2020MagazineRules.snapshotModifier("RIF")).isEqualTo(-2);
            assertThat(Cp2020MagazineRules.snapshotModifier("HVY")).isZero();
        }

        @Test
        @DisplayName("заряд считается по вставленному магазину; обратно в инвентарь магазин выходит своего размера")
        void capacityFollowsMagazine() {
            Item gun = weapon(10, 4, 20, "9mm");
            Item drum = magazine(backpack, "9mm", 50, 50, false);

            String message = perform(gun, "RELOAD", drum);
            assertThat(message).startsWith("Minami: заряжено 50 из 50");
            assertThat(gun.getProperties()).containsEntry("insertedMagazine", "50");

            String fired = Cp2020WeaponActions.perform(gun, "FIRE", Map.of());
            assertThat(fired).endsWith("заряжено 49 из 50");

            Cp2020WeaponActions.perform(gun, "EJECT", Map.of());
            assertThat(gun.getProperties()).doesNotContainKey("insertedMagazine");
            assertThat(magazinesIn(backpack)).filteredOn(mag -> mag.getProperties().get("shots").equals("50"))
                    .hasSize(1).first().satisfies(mag -> assertThat(loaded(mag)).isEqualTo(49));
        }

        @Test
        @DisplayName("стандартный магазин снимает размер вставленного")
        void backToStandard() {
            Item gun = weapon(10, 4, 20, "9mm");
            perform(gun, "RELOAD", magazine(backpack, "9mm", 20, 20, false));
            assertThat(gun.getProperties()).containsEntry("insertedMagazine", "20");

            perform(gun, "RELOAD", magazine(backpack, "9mm", 10, 10, false));
            assertThat(gun.getProperties()).doesNotContainKey("insertedMagazine");
        }

        @Test
        @DisplayName("штрафы показываются только при магазине больше двух стандартных")
        void notes() {
            Item gun = weapon(10, 4, 20, "9mm");
            gun.getProperties().put("reliability", "VR");
            gun.getProperties().put("concealability", "J");
            assertThat(Cp2020WeaponActions.notes(gun)).isEmpty();

            perform(gun, "RELOAD", magazine(backpack, "9mm", 20, 20, false));
            assertThat(Cp2020WeaponActions.notes(gun)).isEmpty();

            perform(gun, "RELOAD", magazine(backpack, "9mm", 21, 21, false));
            assertThat(Cp2020WeaponActions.notes(gun))
                    .containsExactly("Большой магазин: надёжность VR → ST, скрытность J → L, инициатива Snapshot +1");

            Cp2020WeaponActions.perform(gun, "EJECT", Map.of());
            assertThat(Cp2020WeaponActions.notes(gun)).isEmpty();
            assertThat(Cp2020WeaponActions.notes(magazine(backpack, "9mm", 50, 50, false))).isEmpty();
        }
    }

    @Nested
    @DisplayName("Снаряжение магазина и оружия без магазина")
    class LoadingFromStack {

        @Test
        @DisplayName("снарядить: добирает до полного, запас уменьшается")
        void topsUp() {
            Item mag = magazine(backpack, "9mm", 10, 4, false);
            Item box = stack(backpack, "9mm", 50, false);

            String message = perform(mag, "LOAD", box);

            assertThat(loaded(mag)).isEqualTo(10);
            assertThat(box.getQuantity()).isEqualTo(44);
            assertThat(message).isEqualTo("Магазин 9mm на 10: заряжено 10 из 10, из запаса взято 6");
        }

        @Test
        @DisplayName("патронов меньше, чем нужно: заряжает сколько есть, пустая коробка исчезает")
        void lastRounds() {
            Item mag = magazine(backpack, "9mm", 10, 4, false);
            Item box = stack(stash, "9mm", 3, false);

            perform(mag, "LOAD", box);

            assertThat(loaded(mag)).isEqualTo(7);
            assertThat(stash.getContent()).isEmpty();
        }

        @Test
        @DisplayName("другой вид: остаток возвращается в такой же запас, в магазине только новые")
        void swapToArmorPiercing() {
            Item mag = magazine(backpack, "9mm", 10, 4, false);
            Item plain = stack(backpack, "9mm", 20, false);
            Item armorPiercing = stack(backpack, "9mm", 30, true);

            String message = perform(mag, "LOAD", armorPiercing);

            assertThat(loaded(mag)).isEqualTo(10);
            assertThat(mag.getProperties()).containsEntry("armorPiercing", "1");
            assertThat(plain.getQuantity()).isEqualTo(24);
            assertThat(armorPiercing.getQuantity()).isEqualTo(20);
            assertThat(message).contains("AP", "взято 10", "возвращено 4");
        }

        @Test
        @DisplayName("другой вид без такого запаса: остаток становится новым запасом рядом")
        void swapCreatesStack() {
            Item mag = magazine(backpack, "9mm", 10, 4, true);
            Item plain = stack(stash, "9mm", 20, false);

            perform(mag, "LOAD", plain);

            assertThat(mag.getProperties()).doesNotContainKey("armorPiercing");
            List<Item> returned = backpack.getContent().stream().filter(item -> item != mag).toList();
            assertThat(returned).hasSize(1);
            assertThat(returned.getFirst().getName()).isEqualTo("Патроны 9mm AP");
            assertThat(returned.getFirst().getQuantity()).isEqualTo(4);
            assertThat(returned.getFirst().getType()).isEqualTo(Cp2020ItemTypes.AMMO);
            assertThat(returned.getFirst().getProperties()).containsEntry("caliber", "9mm").containsEntry("armorPiercing", "1");
        }

        @Test
        @DisplayName("разрядить: все патроны в запас, магазин остаётся пустым")
        void unloads() {
            Item mag = magazine(backpack, "9mm", 10, 4, true);
            Item box = stack(backpack, "9mm", 6, true);

            String message = Cp2020WeaponActions.perform(mag, "UNLOAD", Map.of());

            assertThat(loaded(mag)).isZero();
            assertThat(mag.getProperties()).doesNotContainKey("armorPiercing");
            assertThat(box.getQuantity()).isEqualTo(10);
            assertThat(message).isEqualTo("Магазин 9mm на 10: разряжен, в запас возвращено 4");
        }

        @Test
        @DisplayName("револьвер заряжается патронами прямо из запаса")
        void directLoad() {
            Item gun = revolver(6, 2, ".44");
            Item box = stack(backpack, ".44", 20, false);

            perform(gun, "RELOAD", box);

            assertThat(loaded(gun)).isEqualTo(6);
            assertThat(box.getQuantity()).isEqualTo(16);
        }

        @Test
        @DisplayName("чужой калибр и полный магазин — ошибка, ничего не меняется")
        void rejected() {
            Item mag = magazine(backpack, "9mm", 10, 10, false);
            Item wrong = stack(backpack, "10mm", 50, false);
            Item same = stack(backpack, "9mm", 50, false);

            assertThatThrownBy(() -> perform(mag, "LOAD", wrong)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Cp2020WeaponActions.perform(mag, "LOAD", Map.of())).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> perform(mag, "LOAD", same))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Заряжено полностью");
            assertThat(same.getQuantity()).isEqualTo(50);
            assertThat(wrong.getQuantity()).isEqualTo(50);
        }
    }
}
