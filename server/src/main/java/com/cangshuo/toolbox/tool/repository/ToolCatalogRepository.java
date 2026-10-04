package com.cangshuo.toolbox.tool.repository;

import com.cangshuo.toolbox.tool.model.ToolCatalogEntry;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ToolCatalogRepository {

    private static final String VISIBLE_CATALOG = """
            FROM tool_definition t
            JOIN tool_category c ON c.id = t.category_id
            WHERE t.status = 'ENABLED' AND t.deleted_at IS NULL
              AND c.status = 1 AND c.deleted_at IS NULL
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ToolCatalogRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long countVisible(String categoryCode) {
        return Objects.requireNonNull(jdbc.queryForObject(
                "SELECT COUNT(*) " + filteredCatalog(categoryCode),
                new MapSqlParameterSource("categoryCode", categoryCode), Long.class));
    }

    public List<ToolCatalogEntry> findVisible(String categoryCode, int limit, long offset) {
        String sql = """
                SELECT t.tool_code, t.name, t.description, c.code AS category_code, t.icon,
                       t.keywords_json, t.mode, t.requires_login, t.status,
                       t.version, t.sort_order, t.is_featured
                """ + filteredCatalog(categoryCode) + """
                ORDER BY t.sort_order ASC, t.tool_code ASC
                LIMIT :limit OFFSET :offset
                """;
        var parameters = new MapSqlParameterSource("categoryCode", categoryCode)
                .addValue("limit", limit)
                .addValue("offset", offset);
        return jdbc.query(sql, parameters, (row, rowNumber) -> new ToolCatalogEntry(
                row.getString("tool_code"), row.getString("name"), row.getString("description"),
                row.getString("category_code"), row.getString("icon"), row.getString("keywords_json"),
                row.getString("mode"), row.getBoolean("requires_login"), row.getString("status"),
                row.getInt("version"), row.getInt("sort_order"), row.getBoolean("is_featured")));
    }

    private static String filteredCatalog(String categoryCode) {
        // Only fixed SQL fragments are appended; request values remain bound parameters.
        return VISIBLE_CATALOG + (categoryCode == null ? "" : "AND c.code = :categoryCode\n");
    }
}
