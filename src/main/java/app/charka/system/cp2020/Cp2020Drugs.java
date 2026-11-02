package app.charka.system.cp2020;

import app.charka.effect.EffectTemplate;
import app.charka.effect.EffectTemplate.DurationUnit;
import app.charka.effect.TimedEffects;
import app.charka.item.CatalogItem;
import app.charka.item.ItemAction;
import app.charka.item.ItemField;
import app.charka.item.ItemField.Option;
import app.charka.item.ItemTypeDefinition;
import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.model.counters.strategy.health.DiceRoller;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Препараты (с. 122–123): эффект со сроком на листе и предмет «Препарат», который принимают одним нажатием.
 * Правка стата — только там, где книга даёт число (Boost +1 INT, Black Lace +2 COOL); остальное — заметкой эффекта,
 * его ведёт мастер. Зависимость (провал спасброска) не бросается.
 */
public final class Cp2020Drugs {

    public static final String TYPE = "CP2020_DRUG";
    public static final String DRUG = "drug";
    public static final String USE = "USE";

    private static final String GROUP = "Препараты";

    /** Код, название, цена, стат, правка, срок, единица, заметка */
    private record Drug(String code, String name, int cost, String stat, int modifier, String duration, DurationUnit unit, String note) {
    }

    private static final List<Drug> DRUGS = List.of(
            new Drug("SYNTHCOKE", "SynthCoke", 1000, null, 0, "1d6+1", DurationUnit.MINUTE, "Бодрость; паранойя, психологическая зависимость"),
            new Drug("STIM", "Stim", 500, null, 0, "1d6+1", DurationUnit.MINUTE, "Выносливость, можно не спать; бред"),
            new Drug("SYNCOMP15", "Syncomp 15", 650, Cp2020StatsStrategy.REF, -1, "1d6+1", DurationUnit.ROUND,
                    "Противоядие от нервных и биотоксинов; −1 REF за дозу"),
            new Drug("SPEEDHEAL", "Speedheal", 1650, null, 0, "1d6+1", DurationUnit.HOUR,
                    "Лечение быстрее (с. 116); потом −1D6/3 REF на неделю"),
            new Drug("BOOST", "Boost", 600, Cp2020StatsStrategy.INT, 1, "1d6+1", DurationUnit.HOUR,
                    "У зависимого прибавки нет, без дозы 12 часов — припадки"),
            new Drug("BLUE_GLASS", "Blue Glass", 900, Cp2020StatsStrategy.INT, -1, "1d6+1", DurationUnit.MINUTE,
                    "Под стрессом 3 из 10 — ступор; −1 INT за дозу"),
            new Drug("SMASH", "Smash", 100, null, 0, "1d6+1", DurationUnit.MINUTE,
                    "Легко и весело; при провале зависимости — кататония"),
            new Drug("DORPH", "'Dorph", 250, null, 0, "1d6+1", DurationUnit.ROUND,
                    "Меньше оглушения и шока; 1 на 1D10 — −1 REF навсегда"),
            new Drug("BLACK_LACE", "Black Lace", 650, Cp2020StatsStrategy.COOL, 2, "1d6+1", DurationUnit.HOUR,
                    "Стойкость к оглушению; провал зависимости — −1D6 EMP"));

    public static final List<EffectTemplate> TEMPLATES = DRUGS.stream()
            .map(drug -> new EffectTemplate(drug.code(), drug.name(), drug.stat(), drug.modifier(), drug.duration(), drug.unit(), drug.note()))
            .toList();

    public static final ItemTypeDefinition DRUG_TYPE = new ItemTypeDefinition(TYPE, "Препарат", List.of(
            ItemField.choice(DRUG, "Препарат", true, DRUGS.stream().map(drug -> new Option(drug.code(), drug.name())).toArray(Option[]::new))))
            .inSection(GROUP).withStacking();

    /** Smash продаётся упаковкой по 6 (с. 123): цена за упаковку */
    public static final List<CatalogItem> CATALOG = DRUGS.stream()
            .map(drug -> new CatalogItem("drug-" + drug.code().toLowerCase(), GROUP, drug.name(), TYPE, drug.cost(), drug.note(),
                    Map.of(DRUG, drug.code()), drug.code().equals("SMASH") ? 6 : 1))
            .toList();

    private Cp2020Drugs() {
    }

    public static boolean isDrug(Item item) {
        return TYPE.equals(item.getType());
    }

    public static Optional<EffectTemplate> template(String code) {
        return TEMPLATES.stream().filter(template -> template.code().equals(code)).findFirst();
    }

    /** Что даёт доза: «+1 INT · 1d6+1 ч» вместо кода препарата */
    public static app.charka.item.ItemSummary summary(Item item) {
        return new app.charka.item.ItemSummary(null, template(item.getProperties().get(DRUG)).map(t -> List.of(t.getSummary())).orElse(List.of()));
    }

    public static List<ItemAction> actions(Item item) {
        return List.of(ItemAction.simple(USE, "💊 Принять", null).forCombat());
    }

    /**
     * Принять дозу: одна штука списывается, на персонаже появляется эффект со сроком.
     */
    static String perform(Item item, String action, DiceRoller dice) {
        if (!USE.equals(action)) {
            throw new IllegalArgumentException("Неизвестное действие с препаратом: " + action);
        }
        EffectTemplate template = template(item.getProperties().get(DRUG))
                .orElseThrow(() -> new IllegalArgumentException("Неизвестный препарат"));
        Character owner = item.getInventory().getCharacter();
        var effect = TimedEffects.add(owner, template, dice);
        String extra = template.code().equals("SPEEDHEAL") ? speedheal(owner, dice) : "";
        int left = (item.getQuantity() == null ? 1 : item.getQuantity()) - 1;
        if (left <= 0) {
            item.getInventory().getContent().removeIf(candidate -> candidate == item);
        } else {
            item.setQuantity(left);
        }
        return template.name() + " действует " + effect.getRemainingLabel() + extra
                + (left > 0 ? ", осталось " + left : ", это была последняя доза");
    }

    /**
     * Speedheal (с. 116, 123): лечение быстрее — флаг на счётчике здоровья; побочно −1D6/3 REF на неделю.
     * HOMEBREW: отходняк считается с момента приёма, книга говорит «after use».
     */
    private static String speedheal(Character owner, DiceRoller dice) {
        StringBuilder text = new StringBuilder();
        owner.getCounters().stream()
                .filter(counter -> Cp2020HealthStrategy.TYPE.equals(counter.getType()))
                .findFirst()
                .ifPresent(health -> {
                    health.getAttributes().put(Cp2020HealthStrategy.SPEEDHEAL, 1);
                    text.append("; лечение ускорено");
                });
        int penalty = Math.max(1, dice.roll(6) / 3);
        TimedEffects.add(owner, "Отходняк Speedheal", Cp2020StatsStrategy.REF, -penalty, "7", DurationUnit.DAY,
                "Побочное действие Speedheal", dice);
        return text + "; REF −" + penalty + " на неделю";
    }
}
