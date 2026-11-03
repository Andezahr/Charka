package app.charka.system.cp2020;

import app.charka.model.Character;
import app.charka.model.counters.Counter;
import app.charka.skill.SkillDefinition;
import app.charka.skill.StatValue;
import app.charka.system.PrintSheet;
import app.charka.system.Reference;
import app.charka.system.Reference.Line;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Бумажный лист CP2020 (A-12): статы и производные, SP по зонам, изученные навыки с итогом «стат + навык»,
 * трек здоровья 10 × 4 клетки с названиями уровней — чтобы играть без приложения, отмечая урон карандашом.
 */
final class Cp2020Print {

    private static final Map<String, SkillDefinition> SKILLS = Cp2020Skills.CATALOG.stream()
            .collect(Collectors.toMap(SkillDefinition::code, Function.identity(), (first, second) -> first));

    private Cp2020Print() {
    }

    static PrintSheet sheet(Character character, Map<String, StatValue> stats, Cp2020ArmorStrategy armor) {
        List<Reference> blocks = new ArrayList<>();
        blocks.add(new Reference("Характеристики", null, stats.values().stream()
                .map(stat -> new Line(stat.name(), stat.value() == stat.base() ? String.valueOf(stat.value()) : stat.value() + " (" + stat.base() + ")"))
                .toList()));
        int ma = value(stats, Cp2020StatsStrategy.MA);
        int body = value(stats, Cp2020StatsStrategy.BODY);
        blocks.add(new Reference("Производные", null, List.of(
                new Line("Бег за действие", Cp2020Rules.run(ma) + " м"),
                new Line("Прыжок", Cp2020Rules.leap(Cp2020Rules.run(ma)) + " м"),
                new Line("Переноска", Cp2020Rules.carry(body) + " кг"),
                new Line("BTM", "−" + Cp2020Rules.btm(body)),
                new Line("Спасбросок", "≤ " + body))));
        Optional<Counter> armorCounter = character.getCounters().stream().filter(counter -> Cp2020ArmorStrategy.TYPE.equals(counter.getType())).findFirst();
        armorCounter.ifPresent(counter -> {
            List<Line> zones = new ArrayList<>();
            for (Cp2020Location location : Cp2020Location.values()) {
                zones.add(new Line(location.getLabel() + " (" + location.getRolls() + ")", "SP " + armor.sp(counter, location)));
            }
            blocks.add(new Reference("Броня", null, zones));
        });
        blocks.add(new Reference("Навыки", null, skills(character, stats)));
        int damage = character.getCounters().stream().filter(counter -> Cp2020HealthStrategy.TYPE.equals(counter.getType()))
                .map(Counter::getValue).filter(java.util.Objects::nonNull).findFirst().orElse(0);
        List<PrintSheet.Group> groups = new ArrayList<>();
        for (int level = 0; level < Cp2020Rules.LEVEL_COUNT; level++) {
            groups.add(new PrintSheet.Group(Cp2020Rules.levelName(level), Cp2020Rules.BOXES_PER_LEVEL));
        }
        return new PrintSheet(blocks, new PrintSheet.Track("Здоровье", groups, damage,
                "Попадание: зона → минус SP → в голову ×2 → минус BTM (не меньше 1). Ранен — спасбросок от оглушения; "
                        + "смертельное — спасбросок от смерти каждый раунд."));
    }

    /** Изученные навыки по убыванию итога: «Пистолеты (REF) · 6 · итог 14» */
    private static List<Line> skills(Character character, Map<String, StatValue> stats) {
        return character.getSkills().stream()
                .filter(skill -> skill.getLevel() > 0)
                .map(skill -> {
                    SkillDefinition definition = SKILLS.get(skill.getSkillCode());
                    String name = skill.getCustomName() != null ? skill.getCustomName() : definition != null ? definition.name() : skill.getSkillCode();
                    StatValue stat = definition == null || definition.statCode() == null ? null : stats.get(definition.statCode());
                    int total = skill.getLevel() + (stat == null ? 0 : stat.value());
                    return new Object[]{total, new Line(name + (stat == null ? "" : " (" + stat.code().toUpperCase() + ")"),
                            skill.getLevel() + (stat == null ? "" : " · " + total))};
                })
                .sorted(Comparator.comparing((Object[] row) -> -(int) row[0]))
                .map(row -> (Line) row[1])
                .toList();
    }

    private static int value(Map<String, StatValue> stats, String code) {
        StatValue stat = stats.get(code);
        return stat == null ? Cp2020StatsStrategy.DEFAULT_STAT : stat.value();
    }
}
