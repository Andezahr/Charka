package app.charka.system.cp2020;

import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.npc.NpcSheet;
import app.charka.npc.NpcTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static app.charka.system.cp2020.Cp2020StatsStrategy.*;

/**
 * Быстрые NPC по Fast Character System (с. 30): статы 2D6 девять раз, 11 и выше перебросить; карьерный пакет роли (с. 44) на 40 очков;
 * продвинутый NPC — ещё 2D10 на 5 других навыков; броня и оружие по одному броску D10 с прибавкой роли.
 * Кибер (шаг 3 книги) не бросается: киберware ещё нет, см. G-01.
 * HOMEBREW: сами шаблоны. Книга даёт только порядок создания; какие бывают шаблоны, как 40 очков лежат по навыкам и куда какой бросок стата, решено нами.
 */
final class Cp2020Npcs {

    private static final int STAT_COUNT = 9;
    private static final int REROLL_FROM = 11;
    private static final int TABLE_SIZE = 10;
    private static final String WEAR = "WEAR";

    /** Прибавка к броску снаряжения: Соло +3, Кочевник и Коп +2, прочие 0 (с. 30) */
    private static final int SOLO_GEAR_BONUS = 3;
    private static final int COP_GEAR_BONUS = 2;

    /**
     * Снаряжение по броску (с. 30), строка индекс + 1. HOMEBREW: книга называет класс оружия, образец из справочника выбран нами.
     */
    private static final List<NpcGear> GEAR = List.of(
            new NpcGear("armor_heavy_leather", "knife"),
            new NpcGear("armor_kevlar_vest", "federated_arms_x_22"),
            new NpcGear("armor_light_jacket", "militech_arms_avenger"),
            new NpcGear("armor_light_jacket", "sternmeyer_type_35"),
            new NpcGear("armor_medium_jacket", "sternmeyer_type_35"),
            new NpcGear("armor_medium_jacket", "uzi_miniauto_9"),
            new NpcGear("armor_medium_jacket", "militech_ronin_light_assault"),
            new NpcGear("armor_heavy_jacket", "akr_20_medium_assault"),
            new NpcGear("armor_heavy_jacket", "fn_ral_heavy_assault_rifle"),
            new NpcGear("armor_metalgear", "fn_ral_heavy_assault_rifle"));

    static final List<Template> TEMPLATES = List.of(
            new Template("bandit", "Бандит", "fixer", 0,
                    List.of(REF, COOL, BODY, MA, INT, LUCK, TECH, EMP, ATTR),
                    package10("streetdeal", 3, "awareness", 4, "forgery", 1, "handgun", 7, "brawling", 6,
                            "melee", 6, "pick_lock", 3, "pick_pocket", 3, "intimidate", 5, "persuasion", 2),
                    List.of("streetwise", "stealth", "driving", "submachinegun", "athletics")),
            new Template("cop", "Коп", "cop", COP_GEAR_BONUS,
                    List.of(REF, COOL, INT, BODY, MA, EMP, TECH, LUCK, ATTR),
                    package10("authority", 5, "awareness", 5, "handgun", 7, "human_perception", 3, "athletics", 4,
                            "education", 2, "brawling", 4, "melee", 2, "interrogation", 4, "streetwise", 4),
                    List.of("driving", "rifle", "submachinegun", "first_aid", "stealth")),
            new Template("corp_guard", "Корп-охрана", "solo", SOLO_GEAR_BONUS,
                    List.of(REF, BODY, COOL, MA, INT, TECH, LUCK, ATTR, EMP),
                    package10("combat_sense", 2, "awareness", 6, "handgun", 6, "brawling", 4, "melee", 2,
                            "weaponsmith", 1, "rifle", 7, "athletics", 5, "submachinegun", 6, "stealth", 1),
                    List.of("driving", "first_aid", "electronic_security", "resist_torture", "intimidate")),
            new Template("booster", "Бустер", "solo", SOLO_GEAR_BONUS,
                    List.of(REF, BODY, MA, COOL, LUCK, TECH, INT, ATTR, EMP),
                    package10("combat_sense", 1, "awareness", 3, "handgun", 6, "brawling", 8, "melee", 8,
                            "weaponsmith", 1, "rifle", 1, "athletics", 6, "submachinegun", 5, "stealth", 1),
                    List.of("motorcycle", "intimidate", "streetwise", "endurance", "hide_evade")),
            new Template("merc", "Соло-наёмник", "solo", SOLO_GEAR_BONUS,
                    List.of(REF, COOL, BODY, INT, MA, LUCK, TECH, ATTR, EMP),
                    package10("combat_sense", 6, "awareness", 5, "handgun", 5, "brawling", 3, "melee", 2,
                            "weaponsmith", 3, "rifle", 7, "athletics", 3, "submachinegun", 4, "stealth", 2),
                    List.of("first_aid", "demolitions", "driving", "hide_evade", "intimidate")));

    private Cp2020Npcs() {
    }

    static List<NpcTemplate> templates() {
        return TEMPLATES.stream().map(template -> new NpcTemplate(template.code(), template.label())).toList();
    }

    /**
     * @throws IllegalArgumentException неизвестный шаблон
     */
    static NpcSheet roll(String code, boolean advanced, DiceRoller dice) {
        Template template = TEMPLATES.stream()
                .filter(candidate -> candidate.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Нет такого шаблона NPC: " + code));

        Map<String, Integer> stats = rollStats(template, dice);
        Map<String, Integer> skills = new LinkedHashMap<>(template.career());
        if (advanced) {
            spread(dice.roll(10) + dice.roll(10), template.pickups(), skills);
        }

        int row = Math.min(dice.roll(10) + template.gearBonus(), TABLE_SIZE);
        NpcGear gear = GEAR.get(row - 1);
        return new NpcSheet(Set.of(template.role()),
                Map.of(Cp2020StatsStrategy.TYPE, stats),
                skills,
                List.of(new NpcSheet.Gear(gear.armor(), WEAR), new NpcSheet.Gear(gear.weapon(), null)));
    }

    /** Лучшие броски — первым статам шаблона: книга велит расставить их самому */
    private static Map<String, Integer> rollStats(Template template, DiceRoller dice) {
        List<Integer> rolls = new ArrayList<>();
        for (int i = 0; i < STAT_COUNT; i++) {
            int roll;
            do {
                roll = dice.roll(6) + dice.roll(6);
            } while (roll >= REROLL_FROM);
            rolls.add(roll);
        }
        rolls.sort(Comparator.reverseOrder());
        Map<String, Integer> stats = new LinkedHashMap<>();
        for (int i = 0; i < STAT_COUNT; i++) {
            stats.put(template.statOrder().get(i), rolls.get(i));
        }
        return stats;
    }

    /** HOMEBREW: очки продвинутого NPC делятся поровну, остаток — первым навыкам; книга оставляет это на выбор */
    private static void spread(int points, List<String> pickups, Map<String, Integer> skills) {
        for (int i = 0; i < pickups.size(); i++) {
            int share = points / pickups.size() + (i < points % pickups.size() ? 1 : 0);
            skills.put(pickups.get(i), share);
        }
    }

    private static Map<String, Integer> package10(Object... codesAndLevels) {
        Map<String, Integer> career = new LinkedHashMap<>();
        for (int i = 0; i < codesAndLevels.length; i += 2) {
            career.put((String) codesAndLevels[i], (Integer) codesAndLevels[i + 1]);
        }
        return career;
    }

    private record NpcGear(String armor, String weapon) {
    }

    /**
     * @param role      роль, её пакет навыков и прибавка к снаряжению
     * @param gearBonus прибавка к броску снаряжения
     * @param statOrder статы от лучшего к худшему
     * @param career    карьерные навыки с уровнями, в сумме 40
     * @param pickups   5 других навыков продвинутого NPC
     */
    record Template(String code, String label, String role, int gearBonus, List<String> statOrder,
                    Map<String, Integer> career, List<String> pickups) {
    }
}
