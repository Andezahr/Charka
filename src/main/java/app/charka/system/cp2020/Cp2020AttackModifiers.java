package app.charka.system.cp2020;

import app.charka.attack.AttackForm;
import app.charka.check.CheckRequest;
import app.charka.item.ItemField.Option;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Модификаторы дальней атаки: таблица FNFF (с. 99), значения прочитаны с изображения страницы.
 * Уклонение в таблице только для ближнего боя (C-06), очередь и автоогонь — режимы огня (I-07). Прицельный выстрел задаётся выбором зоны.
 */
final class Cp2020AttackModifiers {

    static final int AIMED_SHOT = -4;
    static final String TELESCOPIC = "scope_telescopic";

    private static final String TARGET = "Цель";
    private static final String SHOOTER = "Стрелок";
    private static final String WEAPON = "Оружие";

    /**
     * @param choice код выбора: варианты с одним кодом взаимоисключающие; у флажка совпадает с {@code code}
     */
    private record Modifier(String code, String section, String choice, String choiceLabel, String label, int value) {

        String text() {
            return label + " " + (value > 0 ? "+" : "−") + Math.abs(value);
        }

        String partName() {
            return choice.equals(code) ? label : choiceLabel + ": " + label;
        }
    }

    private static final List<Modifier> ALL = List.of(
            flag("immobile", TARGET, "Цель неподвижна", 4),
            variant("moving_10", TARGET, "moving", "Цель движется", "REF больше 10", -3),
            variant("moving_12", TARGET, "moving", "Цель движется", "REF больше 12", -4),
            variant("moving_14", TARGET, "moving", "Цель движется", "REF больше 14", -5),
            flag("silhouetted", TARGET, "Цель на просвет", 2),
            variant("size_large", TARGET, "size", "Размер цели", "Большая", 4),
            variant("size_small", TARGET, "size", "Размер цели", "Маленькая", -4),
            variant("size_tiny", TARGET, "size", "Размер цели", "Крошечная", -6),

            variant("aim_1", SHOOTER, "aim", "Прицеливание", "1 раунд", 1),
            variant("aim_2", SHOOTER, "aim", "Прицеливание", "2 раунда", 2),
            variant("aim_3", SHOOTER, "aim", "Прицеливание", "3 раунда", 3),
            flag("fast_draw", SHOOTER, "Быстрое выхватывание", -3),
            flag("ambush", SHOOTER, "Засада", 5),
            flag("blinded", SHOOTER, "Ослеплён светом или пылью", -3),
            flag("turning", SHOOTER, "Разворот к цели", -2),
            flag("two_weapons", SHOOTER, "Стрельба с двух рук", -3),
            flag("running", SHOOTER, "Стрельба на бегу", -3),
            flag("hip", SHOOTER, "Длинноствольное от бедра", -2),

            flag("ricochet", WEAPON, "Рикошет или стрельба навесом", -5),
            flag("turret", WEAPON, "На турели", 2),
            flag("vehicle", WEAPON, "На машине без турели", -4),
            flag("laser", WEAPON, "Лазерный прицел", 1),
            flag("targeting_scope", WEAPON, "Прицельный имплант", 1),
            flag("smartgun", WEAPON, "Смартган", 2),
            flag("smartgoggles", WEAPON, "Смарт-очки", 2));

    private Cp2020AttackModifiers() {
    }

    private static Modifier flag(String code, String section, String label, int value) {
        return new Modifier(code, section, code, label, label, value);
    }

    private static Modifier variant(String code, String section, String choice, String choiceLabel, String label, int value) {
        return new Modifier(code, section, choice, choiceLabel, label, value);
    }

    static List<AttackForm.Section> sections() {
        Map<String, Map<String, List<Modifier>>> bySection = new LinkedHashMap<>();
        ALL.forEach(modifier -> bySection.computeIfAbsent(modifier.section(), key -> new LinkedHashMap<>())
                .computeIfAbsent(modifier.choice(), key -> new ArrayList<>())
                .add(modifier));
        List<AttackForm.Section> sections = new ArrayList<>();
        bySection.forEach((section, choices) -> {
            List<AttackForm.Choice> list = new ArrayList<>(choices.values().stream().map(Cp2020AttackModifiers::choice).toList());
            if (section.equals(WEAPON)) {
                list.add(new AttackForm.Choice(TELESCOPIC, "Телескопический прицел",
                        List.of(new Option(TELESCOPIC, "Телескопический прицел: средняя +1, предельная +2"))));
            }
            sections.add(new AttackForm.Section(section, list));
        });
        return sections;
    }

    private static AttackForm.Choice choice(List<Modifier> variants) {
        Modifier first = variants.get(0);
        return new AttackForm.Choice(first.choice(), first.choiceLabel(),
                variants.stream().map(modifier -> new Option(modifier.code(), modifier.text())).toList());
    }

    /**
     * Слагаемые проверки по выбранным кодам.
     *
     * @param hitNumber сложность по дальности: от неё зависит телескопический прицел
     * @throws IllegalArgumentException на незнакомый код или два варианта одного выбора
     */
    static List<CheckRequest.Part> parts(Set<String> codes, int hitNumber) {
        List<Modifier> chosen = ALL.stream().filter(modifier -> codes.contains(modifier.code())).toList();
        int known = chosen.size() + (codes.contains(TELESCOPIC) ? 1 : 0);
        if (known != codes.size()) {
            throw new IllegalArgumentException("Неизвестный модификатор атаки");
        }
        if (chosen.stream().map(Modifier::choice).distinct().count() != chosen.size()) {
            throw new IllegalArgumentException("Из одного выбора можно взять только один модификатор");
        }
        List<CheckRequest.Part> parts = new ArrayList<>();
        chosen.forEach(modifier -> parts.add(new CheckRequest.Part(modifier.partName(), modifier.value())));
        if (codes.contains(TELESCOPIC)) {
            int scope = hitNumber == Cp2020AttackRules.MEDIUM ? 1 : hitNumber == Cp2020AttackRules.EXTREME ? 2 : 0;
            if (scope != 0) {
                parts.add(new CheckRequest.Part("Телескопический прицел", scope));
            }
        }
        return parts;
    }
}
