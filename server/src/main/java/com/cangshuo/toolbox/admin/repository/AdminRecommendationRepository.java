package com.cangshuo.toolbox.admin.repository;

import com.cangshuo.toolbox.admin.model.AdminRecommendationRow;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminRecommendationRepository {
    private static final String COLUMNS = """
            SELECT id, slot_code, title, subtitle, tool_code, link_url, image_url, sort_order, status,
                   start_at, end_at, updated_at
            FROM home_recommendation
            """;

    private final NamedParameterJdbcTemplate jdbc;
    public AdminRecommendationRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<AdminRecommendationRow> list() {
        return jdbc.query(COLUMNS + " WHERE deleted_at IS NULL ORDER BY sort_order ASC, slot_code ASC LIMIT 128",
                new MapSqlParameterSource(), AdminRecommendationRepository::map);
    }

    public Optional<AdminRecommendationRow> findByCode(String slotCode) {
        return jdbc.query(COLUMNS + " WHERE deleted_at IS NULL AND slot_code = :code",
                new MapSqlParameterSource("code", slotCode), AdminRecommendationRepository::map).stream().findFirst();
    }

    public boolean existsByCode(String slotCode) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM home_recommendation WHERE slot_code = :code",
                new MapSqlParameterSource("code", slotCode), Integer.class);
        return count != null && count > 0;
    }

    public boolean toolExists(String toolCode) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tool_definition WHERE tool_code = :code AND deleted_at IS NULL",
                new MapSqlParameterSource("code", toolCode), Integer.class);
        return count != null && count > 0;
    }

    public void insert(String slotCode, String title, String subtitle, String toolCode, String linkUrl,
                       String imageUrl, int sortOrder, boolean enabled, Instant startAt, Instant endAt) {
        jdbc.update("""
                INSERT INTO home_recommendation (slot_code, title, subtitle, tool_code, link_url, image_url,
                    sort_order, status, start_at, end_at)
                VALUES (:code, :title, :subtitle, :toolCode, :linkUrl, :imageUrl, :sortOrder, :status, :startAt, :endAt)
                """, parameters(slotCode, title, subtitle, toolCode, linkUrl, imageUrl, sortOrder, enabled, startAt, endAt));
    }

    public boolean update(String slotCode, String title, String subtitle, String toolCode, String linkUrl,
                          String imageUrl, int sortOrder, boolean enabled, Instant startAt, Instant endAt) {
        return jdbc.update("""
                UPDATE home_recommendation SET title = :title, subtitle = :subtitle, tool_code = :toolCode,
                    link_url = :linkUrl, image_url = :imageUrl, sort_order = :sortOrder, status = :status,
                    start_at = :startAt, end_at = :endAt
                WHERE slot_code = :code AND deleted_at IS NULL
                """, parameters(slotCode, title, subtitle, toolCode, linkUrl, imageUrl, sortOrder, enabled, startAt, endAt)) == 1;
    }

    public boolean updateStatus(String slotCode, boolean enabled) {
        return jdbc.update("UPDATE home_recommendation SET status = :status WHERE slot_code = :code AND deleted_at IS NULL",
                new MapSqlParameterSource("code", slotCode).addValue("status", enabled ? 1 : 0)) == 1;
    }

    public boolean softDelete(String slotCode) {
        return jdbc.update(
                "UPDATE home_recommendation SET deleted_at = UTC_TIMESTAMP(3) WHERE slot_code = :code AND deleted_at IS NULL",
                new MapSqlParameterSource("code", slotCode)) == 1;
    }

    private static MapSqlParameterSource parameters(String slotCode, String title, String subtitle, String toolCode,
            String linkUrl, String imageUrl, int sortOrder, boolean enabled, Instant startAt, Instant endAt) {
        return new MapSqlParameterSource("code", slotCode).addValue("title", title).addValue("subtitle", subtitle)
                .addValue("toolCode", toolCode).addValue("linkUrl", linkUrl).addValue("imageUrl", imageUrl)
                .addValue("sortOrder", sortOrder).addValue("status", enabled ? 1 : 0)
                .addValue("startAt", startAt == null ? null : java.sql.Timestamp.from(startAt))
                .addValue("endAt", endAt == null ? null : java.sql.Timestamp.from(endAt));
    }

    private static AdminRecommendationRow map(java.sql.ResultSet row, int index) throws java.sql.SQLException {
        Instant startAt = row.getTimestamp("start_at") == null ? null : row.getTimestamp("start_at").toInstant();
        Instant endAt = row.getTimestamp("end_at") == null ? null : row.getTimestamp("end_at").toInstant();
        boolean enabled = row.getInt("status") == 1;
        Instant now = Instant.now();
        boolean active = enabled && (startAt == null || !startAt.isAfter(now)) && (endAt == null || endAt.isAfter(now));
        return new AdminRecommendationRow(row.getLong("id"), row.getString("slot_code"), row.getString("title"),
                row.getString("subtitle"), row.getString("tool_code"), row.getString("link_url"),
                row.getString("image_url"), row.getInt("sort_order"), enabled, startAt, endAt, active,
                row.getTimestamp("updated_at").toInstant());
    }
}
