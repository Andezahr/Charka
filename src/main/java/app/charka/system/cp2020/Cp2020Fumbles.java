package app.charka.system.cp2020;

import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.skill.SkillDefinition;

import java.util.Set;

/**
 * Таблица фамблов CP2020 (книга, с. 43): при «1» на кубике проверки бросается ещё 1d10, раздел зависит от области навыка.
 * В книге разделы названы по действиям (бой, атлетика, ремонт, убеждение, сообразительность); какому навыку какой
 * раздел — наше соответствие, см. {@link #tableFor}. Урон и штрафы из таблицы применяются вручную.
 */
public final class Cp2020Fumbles {

    /** Табличные исходы по диапазонам: до какого броска (включительно) действует текст */
    private record Outcome(int upTo, String text) {
    }

    enum Table {
        COMBAT("боевая",
                new Outcome(4, "обошлось, просто промах"),
                new Outcome(5, "оружие выпало из рук"),
                new Outcome(6, "случайный выстрел (не автомат — бросок надёжности) или удар во что-то безобидное"),
                new Outcome(7, "оружие заклинило (не автомат — бросок надёжности) или застряло в земле на ход"),
                new Outcome(8, "ранил себя — бросьте зону попадания"),
                new Outcome(10, "ранил союзника")),
        ATHLETICS("атлетика",
                new Outcome(4, "обошлось, просто выглядит глупо"),
                new Outcome(7, "полная неудача: 1 очко лёгкого урона и спасбросок от оглушения"),
                new Outcome(10, "тяжёлая неудача: при физическом действии 1d6 урона и спасбросок от оглушения с −1")),
        TECH("техника",
                new Outcome(4, "обошлось, просто не получилось"),
                new Outcome(7, "стало хуже: инструменты выпали или вещь повреждена, сложность +5, можно пробовать снова"),
                new Outcome(10, "вещь испорчена без возможности ремонта: нужна новая")),
        EMPATHY("эмпатия",
                new Outcome(4, "обошлось, просто не поверили"),
                new Outcome(6, "собеседник охладел: −4 к следующему броску EMP"),
                new Outcome(10, "собеседник яростно против всего, что вы предлагаете; бросьте 1d10: на 1–4 он нападает")),
        INTELLECT("интеллект",
                new Outcome(4, "обошлось, просто не знаете, как быть, и не замечаете этого"),
                new Outcome(7, "ничего не понимаете; убеждение с −2, чтобы этого никто не заметил"),
                new Outcome(10, "совсем не в теме, и это видят все"));

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
            "archery", "brawling", "fencing", "handgun", "heavy_weapons", "melee", "rifle", "submachinegun",
            "martial_art", "aikido", "animal_kung_fu", "boxing", "capoeira", "choi_li_fut", "judo", "karate",
            "tae_kwon_do", "thai_kick_boxing", "wrestling");
    /** Спецспособности на INT, которые книга описывает как убеждение (с. 46) */
    private static final Set<String> PERSUASION_SKILLS = Set.of("credibility", "resources");

    private Cp2020Fumbles() {
    }

    /**
     * Раздел таблицы для навыка; свободная проверка или неизвестный навык — null: области нет, раздел выбирает мастер.
     * COOL и ATTR относим к эмпатии (воздействие на людей), BODY — к атлетике.
     */
    static Table tableFor(String skillCode) {
        if (skillCode == null) {
            return null;
        }
        if (COMBAT_SKILLS.contains(skillCode)) {
            return Table.COMBAT;
        }
        if (PERSUASION_SKILLS.contains(skillCode)) {
            return Table.EMPATHY;
        }
        return Cp2020Skills.CATALOG.stream()
                .filter(skill -> skill.code().equals(skillCode))
                .map(SkillDefinition::statCode)
                .findFirst()
                .map(stat -> switch (stat) {
                    case Cp2020StatsStrategy.REF, Cp2020StatsStrategy.BODY -> Table.ATHLETICS;
                    case Cp2020StatsStrategy.TECH -> Table.TECH;
                    case Cp2020StatsStrategy.INT -> Table.INTELLECT;
                    default -> Table.EMPATHY;
                })
                .orElse(null);
    }

    /**
     * Бросок по таблице и шаг расчёта: «Таблица фамблов (боевая), d10 = 5: оружие выпало из рук».
     * Без области навыка кубик не бросается: раздел сначала выбирает мастер.
     */
    static String roll(String skillCode, DiceRoller dice) {
        Table table = tableFor(skillCode);
        if (table == null) {
            return "Таблица фамблов: область не задана, раздел выбирает мастер";
        }
        int roll = dice.d10();
        return "Таблица фамблов (" + table.title() + "), d10 = " + roll + ": " + table.outcome(roll);
    }
}
