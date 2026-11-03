package app.charka.undo;

import app.charka.item.ItemAction;
import app.charka.model.Item;
import app.charka.repository.ItemRepository;
import app.charka.system.GameSystemRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Подпись отмены по адресу действия (A-01): «Колясик: выстрел очередью — Militech Avenger». Считается до действия:
 * предмет может израсходоваться.
 */
@Component
@RequiredArgsConstructor
public class UndoLabels {

    private static final Pattern ITEM_ACTION = Pattern.compile("/items/(\\d+)/action/([A-Za-z_]+)$");
    private static final Pattern ITEM = Pattern.compile("/items/(\\d+)(?:/(\\w+))?$");
    private static final Pattern PARTICIPANT = Pattern.compile("/encounter/participants/(\\d+)/(\\w+)");

    /** Последний кусок адреса → что сделали */
    private static final Map<String, String> WORDS = Map.ofEntries(
            Map.entry("damage", "урон"), Map.entry("heal", "лечение"), Map.entry("stabilize", "стабилизация"),
            Map.entry("care", "медпомощь"), Map.entry("money", "деньги"), Map.entry("expenses", "регулярная трата"),
            Map.entry("wounds", "журнал здоровья"), Map.entry("effects", "эффект"), Map.entry("improve", "прокачка навыка"),
            Map.entry("skills", "навыки"), Map.entry("roles", "роли"), Map.entry("custom", "свой навык"),
            Map.entry("next", "следующий ход"), Map.entry("start", "начало боя"), Map.entry("end", "конец боя"),
            Map.entry("chronicles", "промотка времени"), Map.entry("downtime", "даунтайм"), Map.entry("award", "начисление"), Map.entry("session", "новая сессия"),
            Map.entry("dismiss", "заряд никого не задел"), Map.entry("quantity", "количество"), Map.entry("delete", "удаление"),
            Map.entry("edit", "правка"), Map.entry("move", "перенос"), Map.entry("catalog", "из справочника"),
            Map.entry("items", "новый предмет"), Map.entry("attributes", "правка счётчика"), Map.entry("reset", "сброс счётчика"),
            Map.entry("update", "счётчик"), Map.entry("set", "счётчик"), Map.entry("create", "новый счётчик"),
            Map.entry("wait", "ожидание"), Map.entry("join", "вступление в бой"), Map.entry("remove", "убран из боя"),
            Map.entry("initiative", "инициатива"), Map.entry("participants", "новый участник боя"), Map.entry("check", "проверка"),
            Map.entry("inventories", "инвентарь"), Map.entry("installed", "вживлённое"), Map.entry("setup", "счётчики системы"));

    private final JdbcTemplate jdbc;
    private final ItemRepository itemRepository;
    private final GameSystemRegistry systemRegistry;

    /**
     * @param catalogKey что добавляют из справочника; null — не оттуда
     * @param itemName   название своего добавляемого предмета; null — не добавление
     */
    @Transactional(readOnly = true)
    public String label(String path, Long characterId, boolean buy, String catalogKey, String itemName) {
        String who = characterId == null ? null : name("SELECT name FROM character WHERE id = ?", characterId);
        String what = what(path, buy);
        String added = catalogKey != null ? catalogName(characterId, catalogKey) : itemName;
        if (added != null && !added.isBlank() && (path.endsWith("/items") || path.endsWith("/items/catalog"))) {
            what = (buy ? "покупка" : "новый предмет") + " — " + added.strip();
        }
        String text = who == null ? capitalize(what) : who + ": " + what;
        return text.length() > 255 ? text.substring(0, 255) : text;
    }

    private String what(String path, boolean buy) {
        Matcher action = ITEM_ACTION.matcher(path);
        if (action.find()) {
            return itemAction(Long.valueOf(action.group(1)), action.group(2));
        }
        if (path.contains("/attack/ability/")) {
            return "атака без оружия";
        }
        if (path.contains("/attack/blast/")) {
            return path.endsWith("/dismiss") ? WORDS.get("dismiss") : "урон от заряда";
        }
        Matcher item = ITEM.matcher(path);
        if (item.find()) {
            String name = itemName(Long.valueOf(item.group(1)));
            String verb = "attack".equals(item.group(2)) ? "атака" : buy ? "покупка" : WORDS.getOrDefault(item.group(2), "предмет");
            return name == null ? verb : verb + " — " + name;
        }
        if (buy) {
            return "покупка";
        }
        Matcher participant = PARTICIPANT.matcher(path);
        if (participant.find()) {
            String name = name("SELECT c.name FROM encounter_participant p JOIN character c ON p.character_id = c.id WHERE p.id = ?",
                    Long.valueOf(participant.group(1)));
            String verb = path.contains("/save/") ? "спасбросок" : path.contains("/contest/") ? "противостояние"
                    : path.contains("/declare/") ? "объявление" : WORDS.getOrDefault(participant.group(2), "бой");
            return name == null ? verb : verb + " — " + name;
        }
        String[] parts = path.split("/");
        for (int index = parts.length - 1; index >= 0; index--) {
            String word = WORDS.get(parts[index]);
            if (word != null) {
                return path.contains("/health/save/") ? "спасбросок" : word;
            }
        }
        return "действие";
    }

    private String itemAction(Long itemId, String code) {
        Item item = itemRepository.findById(itemId).orElse(null);
        if (item == null) {
            return code;
        }
        String label = code;
        if (item.getInventory() != null && item.getInventory().getCharacter() != null) {
            List<ItemAction> actions = systemRegistry.forCharacter(item.getInventory().getCharacter()).getItemActions(item);
            label = actions.stream().filter(candidate -> candidate.code().equals(code)).map(ItemAction::label)
                    .findFirst().orElse(code);
        }
        return plain(label).toLowerCase() + " — " + item.getName();
    }

    private String catalogName(Long characterId, String key) {
        if (characterId == null) {
            return null;
        }
        return jdbc.queryForList("SELECT c.game_system FROM character ch JOIN campaign c ON ch.campaign_id = c.id WHERE ch.id = ?",
                        String.class, characterId).stream().findFirst()
                .flatMap(systemRegistry::find)
                .flatMap(system -> system.getItemCatalog().stream().filter(entry -> entry.key().equals(key)).findFirst())
                .map(app.charka.item.CatalogItem::name)
                .orElse(null);
    }

    private String itemName(Long itemId) {
        return name("SELECT name FROM item WHERE id = ?", itemId);
    }

    private String name(String sql, Long id) {
        List<String> names = jdbc.queryForList(sql, String.class, id);
        return names.isEmpty() ? null : names.get(0);
    }

    /** Без значка в начале и цены в конце: «⚡ Очередь · 10» → «Очередь» */
    private static String plain(String label) {
        String text = label.replaceAll("^[^\\p{L}\\d]+", "");
        int dot = text.indexOf(" · ");
        return (dot > 0 ? text.substring(0, dot) : text).strip();
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
