package app.charka.system.cp2020;

import app.charka.system.Reference;
import app.charka.system.Reference.Line;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Справка правил CP2020 под рукой (A-11): числа — из тех же классов правил, что считают бросок, страницы — книги.
 * Что не число (порядок урона, действия в раунде), сформулировано здесь коротко, по выжимке {@code RULES.md}.
 */
final class Cp2020Reference {

    private Cp2020Reference() {
    }

    static List<Reference> sections() {
        List<Reference> sections = new ArrayList<>();
        sections.add(difficulties());
        sections.add(new Reference("Дальность", "с. 99", List.of(
                new Line("В упор (до 1 м)", String.valueOf(Cp2020AttackRules.POINT_BLANK)),
                new Line("Близко: до ¼ дальности оружия", String.valueOf(Cp2020AttackRules.CLOSE)),
                new Line("Средне: до ½ дальности", String.valueOf(Cp2020AttackRules.MEDIUM)),
                new Line("Далеко: до полной дальности", String.valueOf(Cp2020AttackRules.LONG)),
                new Line("Предельно: до двойной дальности", String.valueOf(Cp2020AttackRules.EXTREME)))));
        sections.add(new Reference("Модификаторы атаки", "с. 99", Cp2020AttackModifiers.referenceLines().stream()
                .map(pair -> new Line(pair[0], pair[1])).toList()));
        sections.add(new Reference("Действия в раунде", "с. 98", List.of(
                new Line("Раунд", "≈ 3 с"),
                new Line("Одно действие без штрафа; каждое следующее", "−3, −6, −9 (растёт, HOMEBREW)"),
                new Line("Бег", "MA × 3 м"),
                new Line("Атака до ROF или удар в ближнем бою; уклонение, парирование; вырваться из захвата; прицелиться (+1 за раунд, до 3); "
                        + "перезарядить или сменить оружие; сесть в машину или выйти; ремонт или медпомощь; прочее", null))));
        sections.add(new Reference("Порядок урона", "с. 102–104", List.of(
                new Line("1. Зона попадания", "1D10 или прицельно −4"),
                new Line("2. Минус SP брони зоны; пробил — броня −1 SP", null),
                new Line("3. В голову — вдвое", "×2"),
                new Line("4. Минус BTM, но не меньше 1", null),
                new Line("5. Больше 8 в голову — смерть; больше 8 в конечность — потеря конечности и спасбросок от смерти", null),
                new Line("6. Спасбросок от оглушения: 1D10 ≤ BODY минус штраф уровня ранения", null))));
        sections.add(new Reference("Лечение", "с. 115–116", List.of(
                new Line("Стабилизация: TECH + медицина + 1D10 ≥ весь урон", null),
                new Line("Без успешной проверки медика", "+" + Cp2020HealthStrategy.UNTREATED_DAMAGE_PER_DAY + " урона в день"),
                new Line("First Aid", "−0.5 в день"),
                new Line("Medical Tech", "−1 в день"),
                new Line("Speedheal, нанотехника", "ещё по −2, всё вместе −3"))));
        sections.add(new Reference("Фамблы: на 1 — ещё 1D10", "с. 43", fumbles()));
        return sections;
    }

    private static Reference difficulties() {
        List<Line> lines = new ArrayList<>();
        for (Map.Entry<String, Integer> preset : new Cp2020CheckResolver().getDifficultyPresets().entrySet()) {
            lines.add(new Line(preset.getKey(), String.valueOf(preset.getValue())));
        }
        lines.add(new Line("Проверка: стат + навык + 1D10 ≥ сложности; на 10 — ещё 1D10", null));
        return new Reference("Сложность проверки", "с. 41", lines);
    }

    private static List<Line> fumbles() {
        List<Line> lines = new ArrayList<>();
        for (Cp2020Fumbles.Table table : Cp2020Fumbles.Table.values()) {
            lines.add(new Line(table.title(), String.join(" · ", table.ranges())));
        }
        return lines;
    }
}
