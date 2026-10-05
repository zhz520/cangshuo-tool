package com.cangshuo.toolbox.admin.repository;

import com.cangshuo.toolbox.admin.model.AdminCategoryResponse;
import com.cangshuo.toolbox.admin.model.AdminToolRow;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminToolRepository {
    private static final String COLUMNS = """
            SELECT t.id, t.tool_code, t.name, t.description, c.code AS category_code, c.name AS category_name,
                   t.icon, t.keywords_json, t.mode, t.requires_login, t.status, t.version, t.sort_order,
                   t.is_featured, t.config_json, t.updated_at
            """;
    private static final String SOURCE = """
            FROM tool_definition t
            JOIN tool_category c ON c.id = t.category_id
            WHERE t.deleted_at IS NULL
            """;

    private final NamedParameterJdbcTemplate jdbc;
    public AdminToolRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public long count(String keyword, String categoryCode, String status) {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*)" + source(keyword, categoryCode, status),
                filters(keyword, categoryCode, status), Long.class));
    }

    public List<AdminToolRow> find(String keyword, String categoryCode, String status, int limit, long offset) {
        var parameters = filters(keyword, categoryCode, status).addValue("limit", limit).addValue("offset", offset);
        return jdbc.query(COLUMNS + source(keyword, categoryCode, status)
                + " ORDER BY t.sort_order ASC, t.tool_code ASC LIMIT :limit OFFSET :offset", parameters,
                AdminToolRepository::map);
    }

    public Optional<AdminToolRow> findByCode(String code) {
        return jdbc.query(COLUMNS + SOURCE + " AND t.tool_code = :code", new MapSqlParameterSource("code", code),
                AdminToolRepository::map).stream().findFirst();
    }

    /** The unique index also covers soft-deleted rows, so uniqueness checks ignore deleted_at. */
    public boolean existsByCode(String code) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM tool_definition WHERE tool_code = :code",
                new MapSqlParameterSource("code", code), Integer.class);
        return count != null && count > 0;
    }

    public boolean categoryUsable(String categoryCode) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tool_category WHERE code = :code AND deleted_at IS NULL",
                new MapSqlParameterSource("code", categoryCode), Integer.class);
        return count != null && count > 0;
    }

    public List<AdminCategoryResponse> categories() {
        return jdbc.query("""
                SELECT code, name, sort_order, status FROM tool_category
                WHERE deleted_at IS NULL ORDER BY sort_order ASC, code ASC LIMIT 64
                """, new MapSqlParameterSource(), (row, index) -> new AdminCategoryResponse(
                row.getString("code"), row.getString("name"), row.getInt("sort_order"), row.getInt("status") == 1));
    }

    public void insert(String code, String name, String description, String categoryCode, String icon,
                       String keywordsJson, String mode, boolean requiresLogin, String status, int version,
                       int sortOrder, boolean featured, String configJson) {
        jdbc.update("""
                INSERT INTO tool_definition (tool_code, name, description, category_id, icon, keywords_json, mode,
                    requires_login, status, version, sort_order, is_featured, config_json)
                VALUES (:code, :name, :description, (SELECT id FROM tool_category WHERE code = :categoryCode),
                    :icon, :keywords, :mode, :requiresLogin, :status, :version, :sortOrder, :featured, :config)
                """, baseParameters(code, name, description, categoryCode, icon, keywordsJson, mode, requiresLogin,
                status, version, sortOrder, featured, configJson));
    }

    public boolean update(String code, String name, String description, String categoryCode, String icon,
                          String keywordsJson, String mode, boolean requiresLogin, String status, int version,
                          int sortOrder, boolean featured, String configJson) {
        return jdbc.update("""
                UPDATE tool_definition SET name = :name, description = :description,
                    category_id = (SELECT id FROM tool_category WHERE code = :categoryCode), icon = :icon,
                    keywords_json = :keywords, mode = :mode, requires_login = :requiresLogin, status = :status,
                    version = :version, sort_order = :sortOrder, is_featured = :featured, config_json = :config
                WHERE tool_code = :code AND deleted_at IS NULL
                """, baseParameters(code, name, description, categoryCode, icon, keywordsJson, mode, requiresLogin,
                status, version, sortOrder, featured, configJson)) == 1;
    }

    public boolean updateStatus(String code, String status) {
        return jdbc.update("UPDATE tool_definition SET status = :status WHERE tool_code = :code AND deleted_at IS NULL",
                new MapSqlParameterSource("code", code).addValue("status", status)) == 1;
    }

    public boolean softDelete(String code) {
        return jdbc.update(
                "UPDATE tool_definition SET deleted_at = UTC_TIMESTAMP(3) WHERE tool_code = :code AND deleted_at IS NULL",
                new MapSqlParameterSource("code", code)) == 1;
    }

    private static String source(String keyword, String categoryCode, String status) {
        return SOURCE
                + (keyword == null ? "" : " AND (LOCATE(:keyword, t.tool_code) > 0 OR LOCATE(:keyword, t.name) > 0"
                        + " OR LOCATE(:keyword, t.description) > 0)")
                + (categoryCode == null ? "" : " AND c.code = :categoryCode")
                + (status == null ? "" : " AND t.status = :status");
    }

    private static MapSqlParameterSource filters(String keyword, String categoryCode, String status) {
        return new MapSqlParameterSource("keyword", keyword).addValue("categoryCode", categoryCode)
                .addValue("status", status);
    }

    private static MapSqlParameterSource baseParameters(String code, String name, String description,
            String categoryCode, String icon, String keywordsJson, String mode, boolean requiresLogin,
            String status, int version, int sortOrder, boolean featured, String configJson) {
        return new MapSqlParameterSource("code", code).addValue("name", name)
                .addValue("description", description).addValue("categoryCode", categoryCode)
                .addValue("icon", icon).addValue("keywords", keywordsJson).addValue("mode", mode)
                .addValue("requiresLogin", requiresLogin).addValue("status", status).addValue("version", version)
                .addValue("sortOrder", sortOrder).addValue("featured", featured).addValue("config", configJson);
    }

    private static AdminToolRow map(java.sql.ResultSet row, int index) throws java.sql.SQLException {
        return new AdminToolRow(row.getLong("id"), row.getString("tool_code"), row.getString("name"),
                row.getString("description"), row.getString("category_code"), row.getString("category_name"),
                row.getString("icon"), row.getString("keywords_json"), row.getString("mode"),
                row.getBoolean("requires_login"), row.getString("status"), row.getInt("version"),
                row.getInt("sort_order"), row.getBoolean("is_featured"), row.getString("config_json"),
                row.getTimestamp("updated_at").toInstant());
    }
}
