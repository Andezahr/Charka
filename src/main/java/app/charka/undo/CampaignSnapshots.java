package app.charka.undo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Снимок игрового состояния кампании (A-01): строки таблиц с прежними id — дата кампании, хроника, бой
 * и у каждого персонажа счётчики, предметы, деньги, траты, эффекты, журнал здоровья, навыки, роли, заряды.
 * Восстановление заменяет эти строки строками снимка. Не входят: сами персонажи, заметки, контакты, карты, лог бросков и чат.
 * Положение фишек и копия карты боя не откатываются: их правят перетаскиванием, а ход по карте отменой не считается.
 */
@Component
@RequiredArgsConstructor
public class CampaignSnapshots {

    /** Таблица снимка; {@code where} — условие с {C} (кампания) и {X} (персонажи снимка) */
    private record Table(String name, String where) {
    }

    private static final String ENCOUNTER = "SELECT id FROM encounter WHERE campaign_id = {C}";
    private static final String PARTICIPANTS = "SELECT p.id FROM encounter_participant p JOIN encounter e ON p.encounter_id = e.id WHERE e.campaign_id = {C}";
    private static final String EFFECTS = "SELECT ce.id FROM combat_effect ce JOIN encounter_participant p ON ce.participant_id = p.id "
            + "JOIN encounter e ON p.encounter_id = e.id WHERE e.campaign_id = {C}";
    private static final String INVENTORIES = "SELECT id FROM inventory WHERE character_id IN {X}";

    /** Сначала родители: так вставлять; удалять — в обратном порядке */
    private static final List<Table> TABLES = List.of(
            new Table("chronicle", "campaign_id = {C}"),
            new Table("encounter", "campaign_id = {C}"),
            new Table("encounter_participant", "encounter_id IN (" + ENCOUNTER + ")"),
            new Table("combat_effect", "participant_id IN (" + PARTICIPANTS + ")"),
            new Table("combat_effect_data", "effect_id IN (" + EFFECTS + ")"),
            new Table("character_role", "character_id IN {X}"),
            new Table("character_skill", "character_id IN {X}"),
            new Table("character_profile", "character_id IN {X}"),
            new Table("wound", "character_id IN {X}"),
            new Table("money", "character_id IN {X}"),
            new Table("recurring_expense", "character_id IN {X}"),
            new Table("timed_effect", "character_id IN {X}"),
            new Table("counter", "character_id IN {X}"),
            new Table("counter_attribute", "counter_id IN (SELECT id FROM counter WHERE character_id IN {X})"),
            new Table("counter_text", "counter_id IN (SELECT id FROM counter WHERE character_id IN {X})"),
            new Table("inventory", "character_id IN {X}"),
            new Table("item", "inventory_id IN (" + INVENTORIES + ")"),
            new Table("item_property", "item_id IN (SELECT id FROM item WHERE inventory_id IN (" + INVENTORIES + "))"),
            new Table("pending_blast", "character_id IN {X}"),
            new Table("pending_blast_data", "blast_id IN (SELECT id FROM pending_blast WHERE character_id IN {X})"),
            new Table("pending_blast_target", "blast_id IN (SELECT id FROM pending_blast WHERE character_id IN {X})"));

    /** Что не откатывается и не делает действие отменяемым: карта боя и положение фишек */
    private static final Map<String, Set<String>> KEPT = Map.of(
            "encounter", Set.of("map_doc"),
            "encounter_participant", Set.of("pos_x", "pos_y", "pos_level"));

    /**
     * @param currentDate игровая дата кампании, ISO
     * @param characters  персонажи кампании на момент снимка
     * @param tables      строки по таблицам: столбец → значение (даты — строкой ISO)
     */
    public record Snapshot(long campaignId, String currentDate, List<Long> characters, Map<String, List<Map<String, Object>>> tables) {
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Map<String, Map<String, Integer>> columnTypes = new java.util.concurrent.ConcurrentHashMap<>();

    public Snapshot take(long campaignId) {
        List<Long> characters = jdbc.queryForList("SELECT id FROM character WHERE campaign_id = ? ORDER BY id", Long.class, campaignId);
        String date = jdbc.query("SELECT current_date_value FROM campaign WHERE id = ?",
                rs -> rs.next() ? (String) value(rs, 1) : null, campaignId);
        Map<String, List<Map<String, Object>>> tables = new LinkedHashMap<>();
        for (Table table : TABLES) {
            tables.put(table.name(), rows(table, campaignId, characters));
        }
        return new Snapshot(campaignId, date, characters, tables);
    }

    /** Состояние то же, что в снимке: действие ничего не изменило */
    public boolean same(Snapshot a, Snapshot b) {
        return fingerprint(a).equals(fingerprint(b));
    }

    public String toJson(Snapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Снимок не записать в JSON", e);
        }
    }

    public Snapshot fromJson(String json) {
        try {
            return objectMapper.readValue(json, Snapshot.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Снимок не прочитать", e);
        }
    }

    /**
     * Заменяет игровое состояние кампании снимком. Вызывать в транзакции; кэш JPA после этого устарел.
     *
     * @throws IllegalArgumentException если персонажа из снимка уже нет
     */
    public void restore(Snapshot snapshot) {
        long campaignId = snapshot.campaignId();
        List<Long> characters = snapshot.characters();
        if (!characters.isEmpty()) {
            Integer alive = jdbc.queryForObject("SELECT COUNT(*) FROM character WHERE id IN " + ids(characters), Integer.class);
            if (alive == null || alive != characters.size()) {
                throw new IllegalArgumentException("Персонажа из этого действия уже удалили: отменить нельзя");
            }
        }
        Map<Object, Map<String, Object>> kept = keptValues(campaignId);
        for (int index = TABLES.size() - 1; index >= 0; index--) {
            Table table = TABLES.get(index);
            jdbc.update("DELETE FROM " + table.name() + " WHERE " + where(table, campaignId, characters));
        }
        jdbc.update("UPDATE campaign SET current_date_value = ? WHERE id = ?",
                snapshot.currentDate() == null ? null : Date.valueOf(LocalDate.parse(snapshot.currentDate())), campaignId);
        for (Table table : TABLES) {
            for (Map<String, Object> row : snapshot.tables().getOrDefault(table.name(), List.of())) {
                insert(table.name(), withKept(table.name(), row, kept));
            }
        }
    }

    private List<Map<String, Object>> rows(Table table, long campaignId, List<Long> characters) {
        return jdbc.query("SELECT * FROM " + table.name() + " WHERE " + where(table, campaignId, characters) + " ORDER BY 1, 2",
                (rs, rowNum) -> {
                    ResultSetMetaData meta = rs.getMetaData();
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int column = 1; column <= meta.getColumnCount(); column++) {
                        row.put(meta.getColumnLabel(column).toLowerCase(), value(rs, column));
                    }
                    return row;
                });
    }

    /** Значение для JSON: числа, логические, строки; даты — строкой ISO, длинный текст — строкой */
    private static Object value(ResultSet rs, int column) throws SQLException {
        int type = rs.getMetaData().getColumnType(column);
        return switch (type) {
            case Types.DATE -> {
                Date date = rs.getDate(column);
                yield date == null ? null : date.toLocalDate().toString();
            }
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> {
                Timestamp at = rs.getTimestamp(column);
                yield at == null ? null : at.toLocalDateTime().toString();
            }
            case Types.CLOB, Types.NCLOB, Types.LONGVARCHAR, Types.LONGNVARCHAR, Types.VARCHAR, Types.NVARCHAR, Types.CHAR -> rs.getString(column);
            default -> rs.getObject(column);
        };
    }

    private static String where(Table table, long campaignId, List<Long> characters) {
        return table.where().replace("{C}", String.valueOf(campaignId)).replace("{X}", ids(characters));
    }

    private static String ids(List<Long> ids) {
        return ids.isEmpty() ? "(-1)" : ids.stream().map(String::valueOf).collect(Collectors.joining(", ", "(", ")"));
    }

    /** Нынешние карта боя и положение фишек: по таблице и id строки */
    private Map<Object, Map<String, Object>> keptValues(long campaignId) {
        Map<Object, Map<String, Object>> kept = new HashMap<>();
        jdbc.query("SELECT id, map_doc FROM encounter WHERE campaign_id = ?", rs -> {
            kept.put("encounter:" + rs.getLong(1), mapOf("map_doc", rs.getString(2)));
        }, campaignId);
        jdbc.query(PARTICIPANTS.replace("SELECT p.id", "SELECT p.id, p.pos_x, p.pos_y, p.pos_level").replace("{C}", "?"), rs -> {
            kept.put("encounter_participant:" + rs.getLong(1), mapOf("pos_x", rs.getObject(2), "pos_y", rs.getObject(3), "pos_level", rs.getObject(4)));
        }, campaignId);
        return kept;
    }

    private static Map<String, Object> mapOf(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            map.put((String) pairs[index], pairs[index + 1]);
        }
        return map;
    }

    private static Map<String, Object> withKept(String table, Map<String, Object> row, Map<Object, Map<String, Object>> kept) {
        Map<String, Object> current = kept.get(table + ":" + row.get("id"));
        if (current == null) {
            return row;
        }
        Map<String, Object> patched = new LinkedHashMap<>(row);
        patched.putAll(current);
        return patched;
    }

    private void insert(String table, Map<String, Object> row) {
        Map<String, Integer> types = columnTypes(table);
        List<String> columns = new ArrayList<>(row.keySet());
        String sql = "INSERT INTO " + table + " (" + String.join(", ", columns) + ") VALUES ("
                + columns.stream().map(column -> "?").collect(Collectors.joining(", ")) + ")";
        jdbc.update(sql, statement -> {
            for (int index = 0; index < columns.size(); index++) {
                Object value = row.get(columns.get(index));
                int type = types.getOrDefault(columns.get(index), Types.OTHER);
                if (value == null) {
                    statement.setNull(index + 1, type);
                } else {
                    statement.setObject(index + 1, typed(value, type));
                }
            }
        });
    }

    /** Типы столбцов таблицы: схема меняется только миграциями, поэтому запоминаются */
    private Map<String, Integer> columnTypes(String table) {
        return columnTypes.computeIfAbsent(table, name -> jdbc.query("SELECT * FROM " + name + " WHERE 1 = 0", rs -> {
            ResultSetMetaData meta = rs.getMetaData();
            Map<String, Integer> types = new HashMap<>();
            for (int column = 1; column <= meta.getColumnCount(); column++) {
                types.put(meta.getColumnLabel(column).toLowerCase(), meta.getColumnType(column));
            }
            return types;
        }));
    }

    /** Строка ISO обратно в дату, когда столбец — дата */
    private static Object typed(Object value, int type) {
        if (value instanceof String text) {
            if (type == Types.DATE) {
                return Date.valueOf(LocalDate.parse(text));
            }
            if (type == Types.TIMESTAMP || type == Types.TIMESTAMP_WITH_TIMEZONE) {
                return Timestamp.valueOf(LocalDateTime.parse(text));
            }
        }
        return value;
    }

    /** Снимок без того, что не откатывается: по нему сравнивают «до» и «после» */
    private String fingerprint(Snapshot snapshot) {
        Map<String, List<Map<String, Object>>> tables = new LinkedHashMap<>();
        snapshot.tables().forEach((table, rows) -> {
            Set<String> skip = KEPT.getOrDefault(table, Set.of());
            tables.put(table, skip.isEmpty() ? rows : rows.stream().map(row -> {
                Map<String, Object> copy = new LinkedHashMap<>(row);
                skip.forEach(copy::remove);
                return copy;
            }).toList());
        });
        return toJson(new Snapshot(snapshot.campaignId(), snapshot.currentDate(), snapshot.characters(), tables));
    }
}
