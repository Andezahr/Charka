package app.charka.system.cp2020;

import app.charka.attack.AttackForm;
import app.charka.item.ItemField.Option;

import java.util.List;
import java.util.Set;

/**
 * Укрытие цели: таблица «Common Cover SPs» (с. 103). С бронёй складывается как слой (с. 101): {@link Cp2020ArmorRules#combine}.
 */
final class Cp2020Cover {

    static final String CHOICE = "cover";

    private record Cover(String code, String label, int sp) {
    }

    private static final List<Cover> ALL = List.of(
            new Cover("cover_car", "Кузов, дверь машины", 10),
            new Cover("cover_wood_door", "Деревянная дверь", 5),
            new Cover("cover_sheetrock", "Гипсокартон", 5),
            new Cover("cover_block", "Блочная стена", 10),
            new Cover("cover_heavy_door", "Тяжёлая дверь", 15),
            new Cover("cover_steel_door", "Стальная дверь", 20),
            new Cover("cover_brick", "Кирпичная стена", 25),
            new Cover("cover_dataterm", "DataTerm", 25),
            new Cover("cover_mailbox", "Почтовый ящик", 25),
            new Cover("cover_curb", "Бордюр", 25),
            new Cover("cover_stone", "Каменная стена", 30),
            new Cover("cover_tree", "Большое дерево, столб", 30),
            new Cover("cover_engine", "Двигатель", 35),
            new Cover("cover_concrete_pole", "Бетонный столб", 35),
            new Cover("cover_hydrant", "Гидрант", 35),
            new Cover("cover_armored_car", "Бронемашина", 40),
            new Cover("cover_av4", "Корпус AV-4", 40));

    private Cp2020Cover() {
    }

    static AttackForm.Choice choice() {
        return new AttackForm.Choice(CHOICE, "Укрытие цели",
                ALL.stream().map(cover -> new Option(cover.code(), cover.label() + " SP " + cover.sp())).toList());
    }

    static boolean isCover(String code) {
        return ALL.stream().anyMatch(cover -> cover.code().equals(code));
    }

    /**
     * @return SP выбранного укрытия; 0 — цель не за укрытием
     */
    static int sp(Set<String> options) {
        return ALL.stream().filter(cover -> options.contains(cover.code())).mapToInt(Cover::sp).findFirst().orElse(0);
    }
}
