package com.cangshuo.toolbox.admin.repository;

import com.cangshuo.toolbox.admin.model.AdminCategoryRow;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminCategoryRepository {
    private static final String COLUMNS = """
            SELECT c.id, c.code, c.name, c.icon, c.description, c.sort_order, c.status, c.updated_at,
                   (SELECT COUNT(*) FROM tool_definition t
                    WHERE t.category_id = c.id AND t.deleted_at IS NULL) AS tool_count
            FROM tool_category c
            """;

    private final NamedParameterJdbcTemplate jdbc;
    public AdminCategoryRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<AdminCategoryRow> list() {
        return jdbc.query(COLUMNS + " WHERE c.deleted_at IS NULL ORDER BY c.sort_order ASC, c.code ASC LIMIT 128",
                new MapSqlParameterSource(), AdminCategoryRepository::map);
    }

    public Optional<AdminCategoryRow> findByCode(String code) {
        return jdbc.query(COLUMNS + " WHERE c.deleted_at IS NULL AND c.code = :code",
                new MapSqlParameterSource("code", code), AdminCategoryRepository::map).stream().findFirst();
    }

    /** The unique index also covers soft-deleted rows. */
    public boolean existsByCode(String code) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM tool_category WHERE code = :code",
                new MapSqlParameterSource("code", code), Integer.class);
        return count != null && count > 0;
    }

    public long countTools(String code) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM tool_definition t JOIN tool_category c ON c.id = t.category_id
                WHERE c.code = :code AND t.deleted_at IS NULL
                """, new MapSqlParameterSource("code", code), Long.class);
        return Objects.requireNonNullElse(count, 0L);
    }

    public void insert(String code, String name, String icon, String description, int sortOrder, boolean enabled) {
        jdbc.update("""
                INSERT INTO tool_category (code, name, icon, description, sort_order, status)
                VALUES (:code, :name, :icon, :description, :sortOrder, :status)
                """, parameters(code, name, icon, description, sortOrder, enabled));
    }

    public boolean update(String code, String name, String icon, String description, int sortOrder, boolean enabled) {
        return jdbc.update("""
                UPDATE tool_category SET name = :name, icon = :icon, description = :description,
                    sort_order = :sortOrder, status = :status
                WHERE code = :code AND deleted_at IS NULL
                """, parameters(code, name, icon, description, sortOrder, enabled)) == 1;
    }

    public boolean updateStatus(String code, boolean enabled) {
        return jdbc.update("UPDATE tool_category SET status = :status WHERE code = :code AND deleted_at IS NULL",
                new MapSqlParameterSource("code", code).addValue("status", enabled ? 1 : 0)) == 1;
    }

    public boolean softDelete(String code) {
        return jdbc.update(
                "UPDATE tool_category SET deleted_at = UTC_TIMESTAMP(3) WHERE code = :code AND deleted_at IS NULL",
                new MapSqlParameterSource("code", code)) == 1;
    }

    private static MapSqlParameterSource parameters(String code, String name, String icon, String description,
            int sortOrder, boolean enabled) {
        return new MapSqlParameterSource("code", code).addValue("name", name).addValue("icon", icon)
                .addValue("description", description).addValue("sortOrder", sortOrder)
                .addValue("status", enabled ? 1 : 0);
    }

    private static AdminCategoryRow map(java.sql.ResultSet row, int index) throws java.sql.SQLException {
        return new AdminCategoryRow(row.getLong("id"), row.getString("code"), row.getString("name"),
                row.getString("icon"), row.getString("description"), row.getInt("sort_order"),
                row.getInt("status") == 1, row.getInt("tool_count"),
                row.getTimestamp("updated_at").toInstant());
    }
}
