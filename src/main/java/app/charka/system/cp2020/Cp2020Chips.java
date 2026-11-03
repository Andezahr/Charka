package app.charka.system.cp2020;

import app.charka.item.CatalogItem;
import app.charka.item.ItemAction;
import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.skill.SkillOverride;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Чипы навыков (с. 82–83). Чип работает как навык своего уровня (+1…+3) и перекрывает природный уровень, а не складывается с ним.
 * Одновременно работает не больше чипов, чем INT. Рефлекторному чипу (APTR) нужна обкатка: два дня практики на уровень,
 * до того он работает на столько уровней, сколько обкатано. Чипу памяти (MRAM) обкатка не нужна.
 * Цены за уровень — таблица с. 83, прочитана с изображения страницы.
 */
final class Cp2020Chips {

    static final String PRACTICED = "PRACTICED";
    static final int MAX_LEVEL = 3;
    private static final int DAYS_PER_LEVEL = 2;
    private static final String PARENTS = "Chipware Socket|Interface Plugs";

    private Cp2020Chips() {
    }

    /** Таблица с. 83: группа, рефлекторный ли чип, код навыка и цена за уровень. Экспертиза и языки — свои навыки, их чипов здесь нет */
    private record Price(String group, boolean reflex, String skill, int perLevel) {
    }

    private static final List<Price> PRICES = new ArrayList<>();

    private static void prices(String group, boolean reflex, Object... pairs) {
        for (int i = 0; i < pairs.length; i += 2) {
            PRICES.add(new Price(group, reflex, (String) pairs[i], (Integer) pairs[i + 1]));
        }
    }

    static {
        prices("Чипы ATTR и BODY", false, "personal_grooming", 100, "wardrobe_style", 100);
        prices("Чипы ATTR и BODY", true, "swimming", 100);
        prices("Чипы INT", false, "accounting", 150, "anthropology", 150, "biology", 150, "botany", 150, "chemistry", 150,
                "education", 200, "geology", 150, "history", 150, "mathematics", 200, "physics", 200, "programming", 300,
                "stock_market", 300, "wilderness_survival", 200, "zoology", 150);
        prices("Чипы REF", true, "archery", 300, "dance", 150, "driving", 150, "fencing", 300, "handgun", 300, "heavy_weapons", 400,
                "aikido", 350, "animal_kung_fu", 350, "boxing", 350, "capoeira", 350, "choi_li_fut", 350, "judo", 350, "karate", 350,
                "savate", 350, "tae_kwon_do", 350, "thai_kick_boxing", 350, "wrestling", 350,
                "melee", 150, "motorcycle", 150, "heavy_machinery", 200, "pilot_gyro", 300, "pilot_fixed_wing", 300,
                "pilot_dirigible", 300, "pilot_vector_thrust", 350, "rifle", 300, "submachinegun", 300);
        prices("Чипы TECH", true, "aero_tech", 250, "av_tech", 300, "basic_tech", 200, "cryotank", 150, "cyberdeck_design", 200,
                "cybertech", 300, "demolitions", 300, "disguise", 150, "electronics", 150, "electronic_security", 200,
                "first_aid", 150, "forgery", 200, "gyro_tech", 300, "pharmaceuticals", 200, "pick_lock", 150, "pick_pocket", 150,
                "play_instrument", 150, "weaponsmith", 200);
    }

    /** Строки справочника: каждый навык уровнями +1…+3, цена — за уровень × уровень */
    static final List<CatalogItem> CATALOG = catalog();

    private static List<CatalogItem> catalog() {
        Map<String, String> names = new LinkedHashMap<>();
        Cp2020Skills.CATALOG.forEach(skill -> names.put(skill.code(), skill.name()));
        List<CatalogItem> rows = new ArrayList<>();
        for (Price price : PRICES) {
            String skill = names.get(price.skill());
            if (skill == null) {
                throw new IllegalStateException("Нет навыка для чипа: " + price.skill());
            }
            for (int level = 1; level <= MAX_LEVEL; level++) {
                Map<String, String> properties = new HashMap<>();
                properties.put(Cp2020ItemTypes.SURGERY, Cp2020ItemTypes.NO_SURGERY);
                properties.put(Cp2020ItemTypes.HUMANITY_COST, "0");
                properties.put(Cp2020ItemTypes.REQUIRES, PARENTS);
                properties.put(Cp2020ItemTypes.CHIP_SKILL, price.skill());
                properties.put(Cp2020ItemTypes.CHIP_LEVEL, String.valueOf(level));
                if (price.reflex()) {
                    properties.put(Cp2020ItemTypes.CHIP_REFLEX, "1");
                }
                String description = price.reflex()
                        ? "Рефлекторный: обкатка " + DAYS_PER_LEVEL * level + " дн. Перекрывает свой уровень навыка"
                        : "Чип памяти, работает сразу. Перекрывает свой уровень навыка";
                rows.add(new CatalogItem("chip_" + price.skill() + "_" + level, price.group(), "Чип: " + skill + " +" + level,
                        Cp2020ItemTypes.CYBER, price.perLevel() * level, description, properties, 1, true));
            }
        }
        return List.copyOf(rows);
    }

    static boolean isChip(Item item) {
        return Cp2020Cyber.isCyber(item) && item.getProperties().containsKey(Cp2020ItemTypes.CHIP_SKILL);
    }

    private static boolean isReflex(Item chip) {
        return chip.getProperties().containsKey(Cp2020ItemTypes.CHIP_REFLEX);
    }

    static int level(Item chip) {
        return Math.min(MAX_LEVEL, Cp2020WeaponActions.number(chip, Cp2020ItemTypes.CHIP_LEVEL));
    }

    private static int daysNeeded(Item chip) {
        return isReflex(chip) ? DAYS_PER_LEVEL * level(chip) : 0;
    }

    private static int days(Item chip) {
        return Cp2020WeaponActions.number(chip, Cp2020ItemTypes.CHIP_DAYS);
    }

    /** Сколько уровней уже работает: у рефлекторного — по обкатке */
    static int working(Item chip) {
        return isReflex(chip) ? Math.min(level(chip), days(chip) / DAYS_PER_LEVEL) : level(chip);
    }

    private static boolean practiced(Item chip) {
        return days(chip) >= daysNeeded(chip);
    }

    static List<Item> plugged(Character character) {
        return Cp2020Cyber.installed(character).filter(Cp2020Chips::isChip).toList();
    }

    /**
     * Уровень навыка, который задаёт чип: лучший из вставленных, если он уже работает.
     */
    static Optional<SkillOverride> override(Character character, String skillCode) {
        return plugged(character).stream()
                .filter(chip -> skillCode.equals(chip.getProperties().get(Cp2020ItemTypes.CHIP_SKILL)))
                .filter(chip -> working(chip) > 0)
                .max(Comparator.comparingInt(Cp2020Chips::working))
                .map(chip -> new SkillOverride("чип", working(chip)));
    }

    /** Для атак и рукопашной: природный уровень или чип, если он вставлен */
    static int skillLevel(Character character, String skillCode, int natural) {
        return override(character, skillCode).map(SkillOverride::level).orElse(natural);
    }

    /** С. 82: «as many separate chip programs at one time as your current INT stat» */
    static String blockedReason(Item chip) {
        Character owner = chip.getInventory().getCharacter();
        int intelligence = Cp2020Cyber.intelligence(owner);
        int count = plugged(owner).size();
        return count >= intelligence ? "Вставлено чипов: " + count + ", больше INT " + intelligence + " не работает (с. 82)" : null;
    }

    static List<ItemAction> actions(Item chip) {
        List<ItemAction> actions = new ArrayList<>();
        if (!Cp2020Cyber.isInstalled(chip)) {
            String reason = Cp2020Cyber.requiresReason(chip);
            actions.add(ItemAction.simple(Cp2020Cyber.INSTALL, "Вставить", reason != null ? reason : blockedReason(chip)));
            return actions;
        }
        if (!practiced(chip)) {
            // обкатку при создании персонажа или по решению мастера отмечают одним нажатием
            actions.add(ItemAction.simple(PRACTICED, "Обкатан", null).asSecondary());
        }
        actions.add(ItemAction.simple(Cp2020Cyber.REMOVE, "Вынуть", null).asSecondary());
        return actions;
    }

    static String markPracticed(Item chip) {
        chip.getProperties().put(Cp2020ItemTypes.CHIP_DAYS, String.valueOf(daysNeeded(chip)));
        return chip.getName() + ": обкатан, работает на +" + level(chip);
    }

    /**
     * Прошли дни: вставленные рефлекторные чипы обкатываются.
     * HOMEBREW: практика считается сама, пока чип вставлен; книга требует «two full days of practice», не уточняя, что это за дни.
     */
    static void practice(Character character, int days) {
        plugged(character).stream()
                .filter(chip -> !practiced(chip))
                .forEach(chip -> chip.getProperties().put(Cp2020ItemTypes.CHIP_DAYS,
                        String.valueOf(Math.min(daysNeeded(chip), days(chip) + days))));
    }

    /** Подпись фишки: уровень и обкатка */
    static String chipText(Item chip) {
        if (practiced(chip)) {
            return "";
        }
        String practice = "обкатка " + days(chip) + "/" + daysNeeded(chip) + " дн.";
        return working(chip) > 0 ? "работает +" + working(chip) + " · " + practice : practice;
    }

    /** Итог установки: работает ли сразу */
    static String installNote(Item chip) {
        return isReflex(chip) && !practiced(chip)
                ? "Обкатка: " + daysNeeded(chip) + " дн. практики, по +1 за каждые " + DAYS_PER_LEVEL
                : "Работает сразу на +" + level(chip);
    }
}
