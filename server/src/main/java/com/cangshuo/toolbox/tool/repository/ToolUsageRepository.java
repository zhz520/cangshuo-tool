package com.cangshuo.toolbox.tool.repository;

import com.cangshuo.toolbox.tool.model.PopularToolRow;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ToolUsageRepository {

    private static final String VISIBLE_TOOL = """
            FROM tool_definition t
            JOIN tool_category c ON c.id = t.category_id
            WHERE t.status = 'ENABLED' AND t.deleted_at IS NULL
              AND c.status = 1 AND c.deleted_at IS NULL
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ToolUsageRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Only enabled, non-deleted tools in enabled categories may be counted. */
    public boolean visible(String toolCode) {
        return Objects.requireNonNull(jdbc.queryForObject(
                "SELECT COUNT(*) " + VISIBLE_TOOL + " AND t.tool_code = :code",
                new MapSqlParameterSource("code", toolCode), Long.class)) > 0;
    }

    public void increment(String toolCode) {
        jdbc.update("""
                INSERT INTO tool_usage_stat (tool_code, use_count, updated_at)
                VALUES (:code, 1, UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE use_count = use_count + 1, updated_at = UTC_TIMESTAMP(3)
                """, new MapSqlParameterSource("code", toolCode));
        // Both counters participate in ToolUsageService.record's transaction.
        jdbc.update("""
                INSERT INTO tool_usage_daily (usage_date, tool_code, use_count)
                VALUES (DATE(UTC_TIMESTAMP(3) + INTERVAL 8 HOUR), :code, 1)
                ON DUPLICATE KEY UPDATE use_count = use_count + 1
                """, new MapSqlParameterSource("code", toolCode));
    }

    public List<PopularToolRow> findPopular(int limit) {
        return jdbc.query("""
                SELECT t.tool_code, t.name, t.description, t.icon, u.use_count
                FROM tool_usage_stat u
                JOIN tool_definition t ON t.tool_code = u.tool_code
                JOIN tool_category c ON c.id = t.category_id
                WHERE t.status = 'ENABLED' AND t.deleted_at IS NULL
                  AND c.status = 1 AND c.deleted_at IS NULL
                ORDER BY u.use_count DESC, u.updated_at DESC, t.tool_code ASC
                LIMIT :limit
                """,
                new MapSqlParameterSource("limit", limit),
                (row, rowNumber) -> new PopularToolRow(row.getString("tool_code"), row.getString("name"),
                        row.getString("description"), row.getString("icon"), row.getLong("use_count")));
    }
}
