package app.charka.undo;

import app.charka.support.CharkaTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ARC-05: каждая таблица схемы либо снимается отменой ({@code CampaignSnapshots.TABLES}), либо стоит здесь с причиной.
 * Новая таблица без решения роняет тест: иначе «↶» молча не откатит её изменения.
 */
@CharkaTest
@DisplayName("Отмена знает все таблицы схемы (ARC-05)")
class SnapshotTablesTest {

    /** Не игровое состояние или откатывать его не нужно — с причиной */
    private static final Map<String, String> NOT_SNAPSHOTTED = Map.ofEntries(
            Map.entry("flyway_schema_history", "служебная Flyway"),
            Map.entry("app_user", "пользователи — не игра"),
            Map.entry("campaign", "строка кампании остаётся; дата откатывается отдельно в restore"),
            Map.entry("character", "строки персонажей остаются: создание и удаление не отменяются (UndoInterceptor.SKIPPED)"),
            Map.entry("character_pinned_skill", "только место навыка на листе (R-09a, SKIPPED)"),
            Map.entry("battle_map", "заготовки карт — не игра (SKIPPED /maps)"),
            Map.entry("scene", "заготовки боя (A-06, SKIPPED)"),
            Map.entry("scene_npc", "заготовки боя (A-06, SKIPPED)"),
            Map.entry("note", "заметки — не игра (SKIPPED)"),
            Map.entry("note_category", "заметки — не игра"),
            Map.entry("note_category_link", "заметки — не игра"),
            Map.entry("contact", "контакты — не игра (SKIPPED)"),
            Map.entry("chat_message", "чат — лента, не состояние"),
            Map.entry("roll_log", "лог бросков — лента, не состояние"),
            Map.entry("journal_entry", "журнал боя — лента, не состояние"),
            Map.entry("roll_request", "запрос броска уже брошен — отменять нечем (SKIPPED /requests)"),
            Map.entry("undo_entry", "сама отмена"));

    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("Новой таблице нужно решение: в снимок или в исключения с причиной")
    void everyTableDecided() {
        List<String> tables = jdbc.queryForList(
                "SELECT LOWER(table_name) FROM information_schema.tables WHERE LOWER(table_schema) = 'public' AND table_type = 'BASE TABLE'",
                String.class);
        assertThat(tables).hasSizeGreaterThan(20);
        TreeSet<String> undecided = new TreeSet<>(tables);
        undecided.removeAll(CampaignSnapshots.tableNames());
        undecided.removeAll(NOT_SNAPSHOTTED.keySet());
        assertThat(undecided).as("добавьте в CampaignSnapshots.TABLES или сюда с причиной").isEmpty();

        TreeSet<String> stale = new TreeSet<>(CampaignSnapshots.tableNames());
        stale.addAll(NOT_SNAPSHOTTED.keySet());
        stale.removeAll(tables);
        assertThat(stale).as("таблиц больше нет в схеме").isEmpty();
    }
}
