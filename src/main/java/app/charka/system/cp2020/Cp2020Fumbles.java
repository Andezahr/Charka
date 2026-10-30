package app.charka.system.cp2020;

import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.skill.SkillDefinition;

import java.util.Set;

/**
 * Таблицы фамблов CP2020: при «1» на кубике проверки бросается ещё 1d10, таблица зависит от типа навыка.
 * 1–4 — просто неудача, дальше — последствия. Формулировки обобщённые, последствия в цифрах определяет мастер.
 */
public final class Cp2020Fumbles {

    /** Табличные исходы по диапазонам: до какого броска (включительно) действует текст */
    private record Outcome(int upTo, String text) {
    }

    enum Table {
        COMBAT("боевая",
                new Outcome(4, "обошлось, просто промах"),
                new Outcome(5, "оружие выпало из рук"),
                new Outcome(6, "случайный выстрел или удар в случайном направлении"),
                new Outcome(7, "оружие заклинило, холодное застряло на ход"),
                new Outcome(8, "ранил себя — бросьте зону попадания"),
                new Outcome(10, "ранил союзника")),
        REFLEX("ловкость",
                new Outcome(4, "обошлось, просто не вышло"),
                new Outcome(7, "споткнулся или упал, потеряно действие"),
                new Outcome(10, "неудача с травмой")),
        TECH("техника",
                new Outcome(4, "обошлось, просто не получилось"),
                new Outcome(7, "стало хуже: следующая попытка сложнее"),
                new Outcome(10, "сломано всерьёз: без запчастей и ремонта не исправить")),
        MEDICAL("медицина",
                new Outcome(4, "обошлось, пациенту не лучше"),
                new Outcome(7, "пациенту стало хуже: дополнительный урон"),
                new Outcome(10, "тяжёлая ошибка: пациент на грани жизни и смерти")),
        SOCIAL("общение",
                new Outcome(4, "обошлось, просто не поверили"),
                new Outcome(7, "собеседник обиделся или разозлился"),
                new Outcome(10, "собеседник теперь настроен враждебно")),
        INTELLECT("интеллект",
                new Outcome(4, "обошлось, просто не вспомнил"),
                new Outcome(7, "уверен в неверном ответе"),
                new Outcome(10, "грубая ошибка с тяжёлыми последствиями")),
        GENERAL("общая",
                new Outcome(4, "обошлось, просто провал"),
                new Outcome(7, "провал с неприятными последствиями"),
                new Outcome(10, "катастрофический провал"));

        private final String title;
        private final Outcome[] outcomes;

        Table(String title, Outcome... outcomes) {
            this.title = title;
            this.outcomes = outcomes;
        }

        String title() {
            return title;
        }

        String outcome(int roll) {
            for (Outcome outcome : outcomes) {
                if (roll <= outcome.upTo()) {
                    return outcome.text();
                }
            }
            return outcomes[outcomes.length - 1].text();
        }
    }

    private static final Set<String> COMBAT_SKILLS = Set.of(
            "archery", "brawling", "fencing", "handgun", "heavy_weapons", "martial_art", "melee", "rifle", "submachinegun");
    private static final Set<String> MEDICAL_SKILLS = Set.of(
            "first_aid", "medical_tech", "diagnose", "pharmaceuticals", "cryotank");

    private Cp2020Fumbles() {
    }

    /**
     * Таблица навыка; свободная проверка или неизвестный навык — общая.
     */
    static Table tableFor(String skillCode) {
        if (skillCode == null) {
            return Table.GENERAL;
        }
        if (COMBAT_SKILLS.contains(skillCode)) {
            return Table.COMBAT;
        }
        if (MEDICAL_SKILLS.contains(skillCode)) {
            return Table.MEDICAL;
        }
        return Cp2020Skills.CATALOG.stream()
                .filter(skill -> skill.code().equals(skillCode))
                .map(SkillDefinition::statCode)
                .findFirst()
                .map(stat -> switch (stat) {
                    case Cp2020StatsStrategy.REF, Cp2020StatsStrategy.BODY -> Table.REFLEX;
                    case Cp2020StatsStrategy.TECH -> Table.TECH;
                    case Cp2020StatsStrategy.INT -> Table.INTELLECT;
                    case Cp2020StatsStrategy.EMP, Cp2020StatsStrategy.COOL, Cp2020StatsStrategy.ATTR -> Table.SOCIAL;
                    default -> Table.GENERAL;
                })
                .orElse(Table.GENERAL);
    }

    /**
     * Бросок по таблице и шаг расчёта: «Таблица фамблов (боевая), d10 = 5: оружие выпало из рук».
     */
    static String roll(String skillCode, DiceRoller dice) {
        Table table = tableFor(skillCode);
        int roll = dice.d10();
        return "Таблица фамблов (" + table.title() + "), d10 = " + roll + ": " + table.outcome(roll);
    }
}
