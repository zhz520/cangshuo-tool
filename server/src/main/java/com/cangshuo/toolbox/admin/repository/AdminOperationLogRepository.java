package com.cangshuo.toolbox.admin.repository;

import com.cangshuo.toolbox.admin.model.AdminOperationLogRow;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminOperationLogRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public AdminOperationLogRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void record(Long adminId, String module, String operation, String uri, String method, String ip, String result) {
        jdbc.update("""
                INSERT INTO admin_operation_log (admin_id, module, operation, request_uri, request_method, ip, result)
                VALUES (:adminId, :module, :operation, :uri, :method, :ip, :result)
                """, new MapSqlParameterSource("adminId", adminId)
                .addValue("module", cut(module, 32)).addValue("operation", cut(operation, 64))
                .addValue("uri", cut(uri, 255)).addValue("method", cut(method, 10))
                .addValue("ip", cut(ip, 45)).addValue("result", cut(result, 32)));
    }

    public long count(String module, String result, String keyword) {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM admin_operation_log l"
                + filters(module, result, keyword), parameters(module, result, keyword), Long.class));
    }

    public List<AdminOperationLogRow> find(String module, String result, String keyword, int limit, long offset) {
        return jdbc.query("""
                SELECT l.id, l.admin_id, a.username AS admin_username, l.module, l.operation, l.request_uri,
                       l.request_method, l.ip, l.result, l.created_at
                FROM admin_operation_log l LEFT JOIN admin_user a ON a.id = l.admin_id
                """ + filters(module, result, keyword) + " ORDER BY l.id DESC LIMIT :limit OFFSET :offset",
                parameters(module, result, keyword).addValue("limit", limit).addValue("offset", offset),
                AdminOperationLogRepository::mapRow);
    }

    private static String filters(String module, String result, String keyword) {
        return " WHERE 1 = 1"
                + (module == null ? "" : " AND l.module = :module")
                + (result == null ? "" : " AND l.result = :result")
                + (keyword == null ? "" : " AND (LOCATE(:keyword, l.operation) > 0"
                        + " OR LOCATE(:keyword, l.request_uri) > 0)");
    }

    private static MapSqlParameterSource parameters(String module, String result, String keyword) {
        return new MapSqlParameterSource("module", module).addValue("result", result).addValue("keyword", keyword);
    }

    private static AdminOperationLogRow mapRow(java.sql.ResultSet row, int index) throws java.sql.SQLException {
        long adminId = row.getLong("admin_id");
        Long nullableAdminId = row.wasNull() ? null : adminId;
        return new AdminOperationLogRow(row.getLong("id"), nullableAdminId, row.getString("admin_username"),
                row.getString("module"), row.getString("operation"), row.getString("request_uri"),
                row.getString("request_method"), row.getString("ip"), row.getString("result"),
                row.getTimestamp("created_at").toInstant());
    }

    private static String cut(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max) : value;
    }
}
