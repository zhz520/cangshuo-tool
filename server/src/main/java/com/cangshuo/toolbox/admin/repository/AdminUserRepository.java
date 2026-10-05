package com.cangshuo.toolbox.admin.repository;

import com.cangshuo.toolbox.admin.model.AdminUserRow;
import com.cangshuo.toolbox.admin.model.AdminUserSessionResponse;
import com.cangshuo.toolbox.admin.model.AdminUserSyncEntryResponse;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminUserRepository {
    private static final String COLUMNS = """
            SELECT u.id, u.email, u.nickname, u.status, u.last_login_at, u.created_at, u.updated_at,
                   (SELECT COUNT(*) FROM user_sync_entity e
                    WHERE e.user_id = u.id AND e.entity_type = 'FAVORITE' AND e.deleted = 0) AS favorite_count,
                   (SELECT COUNT(*) FROM user_sync_entity e
                    WHERE e.user_id = u.id AND e.entity_type = 'RECENT' AND e.deleted = 0) AS recent_count
            FROM sys_user u
            """;

    private final NamedParameterJdbcTemplate jdbc;
    public AdminUserRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public long count(String keyword, Boolean enabled) {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user u" + filters(keyword, enabled),
                parameters(keyword, enabled), Long.class));
    }

    public List<AdminUserRow> find(String keyword, Boolean enabled, int limit, long offset) {
        var params = parameters(keyword, enabled).addValue("limit", limit).addValue("offset", offset);
        return jdbc.query(COLUMNS + filters(keyword, enabled)
                + " ORDER BY u.id DESC LIMIT :limit OFFSET :offset", params, AdminUserRepository::map);
    }

    public Optional<AdminUserRow> findById(long id) {
        return jdbc.query(COLUMNS + " WHERE u.id = :id", new MapSqlParameterSource("id", id),
                AdminUserRepository::map).stream().findFirst();
    }

    public boolean setEnabled(long id, boolean enabled) {
        return jdbc.update("UPDATE sys_user SET status = :status WHERE id = :id AND status = :current",
                new MapSqlParameterSource("id", id).addValue("status", enabled ? 1 : 0)
                        .addValue("current", enabled ? 0 : 1)) == 1;
    }

    public int revokeSessions(long id) {
        return jdbc.update(
                "UPDATE auth_refresh_session SET revoked_at = UTC_TIMESTAMP(3) WHERE user_id = :id AND revoked_at IS NULL",
                new MapSqlParameterSource("id", id));
    }

    public List<AdminUserSessionResponse> sessions(long id) {
        return jdbc.query("""
                SELECT id, session_code, created_at, expires_at, revoked_at, last_active_at
                FROM auth_refresh_session WHERE user_id = :id ORDER BY id DESC LIMIT 20
                """, new MapSqlParameterSource("id", id), (row, index) -> new AdminUserSessionResponse(
                row.getLong("id"), mask(row.getString("session_code")), instant(row, "created_at"),
                instant(row, "expires_at"), instant(row, "revoked_at"), instant(row, "last_active_at")));
    }

    public List<AdminUserSyncEntryResponse> syncEntries(long id, int limit) {
        return jdbc.query("""
                SELECT entity_type, entity_key, updated_at_ms, deleted FROM user_sync_entity
                WHERE user_id = :id ORDER BY updated_at_ms DESC, entity_type ASC, entity_key ASC LIMIT :limit
                """, new MapSqlParameterSource("id", id).addValue("limit", limit),
                (row, index) -> new AdminUserSyncEntryResponse(row.getString("entity_type"),
                        row.getString("entity_key"), row.getLong("updated_at_ms"), row.getBoolean("deleted")));
    }

    private static String filters(String keyword, Boolean enabled) {
        String keywordFilter = keyword == null ? ""
                : " AND (LOCATE(:keyword, u.email) > 0 OR LOCATE(:keyword, u.nickname) > 0)";
        String statusFilter = enabled == null ? "" : " AND u.status = :status";
        return " WHERE 1 = 1" + keywordFilter + statusFilter;
    }

    private static MapSqlParameterSource parameters(String keyword, Boolean enabled) {
        return new MapSqlParameterSource("keyword", keyword)
                .addValue("status", enabled == null ? null : (enabled ? 1 : 0));
    }

    private static AdminUserRow map(java.sql.ResultSet row, int index) throws java.sql.SQLException {
        return new AdminUserRow(row.getLong("id"), row.getString("email"), row.getString("nickname"),
                row.getInt("status") == 1, instant(row, "last_login_at"), instant(row, "created_at"),
                instant(row, "updated_at"), row.getLong("favorite_count"), row.getLong("recent_count"));
    }

    private static Instant instant(java.sql.ResultSet row, String column) throws java.sql.SQLException {
        java.sql.Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static String mask(String sessionCode) {
        if (sessionCode == null || sessionCode.length() <= 8) return "…";
        return sessionCode.substring(0, 8) + "…";
    }
}
