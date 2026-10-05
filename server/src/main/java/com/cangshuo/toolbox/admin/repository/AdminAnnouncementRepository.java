package com.cangshuo.toolbox.admin.repository;

import com.cangshuo.toolbox.admin.model.AdminAnnouncementRow;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminAnnouncementRepository {
    private static final String COLUMNS = """
            SELECT id, title, body, level, status, start_at, end_at, created_at, updated_at
            FROM announcement
            """;

    private final NamedParameterJdbcTemplate jdbc;
    public AdminAnnouncementRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<AdminAnnouncementRow> list() {
        return jdbc.query(COLUMNS + " WHERE deleted_at IS NULL ORDER BY id DESC LIMIT 128",
                new MapSqlParameterSource(), AdminAnnouncementRepository::map);
    }

    public Optional<AdminAnnouncementRow> findById(long id) {
        return jdbc.query(COLUMNS + " WHERE deleted_at IS NULL AND id = :id",
                new MapSqlParameterSource("id", id), AdminAnnouncementRepository::map).stream().findFirst();
    }

    public long insert(String title, String body, String level, boolean published, Instant startAt, Instant endAt) {
        var keys = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO announcement (title, body, level, status, start_at, end_at)
                VALUES (:title, :body, :level, :status, :startAt, :endAt)
                """, parameters(title, body, level, published, startAt, endAt), keys, new String[]{"id"});
        return java.util.Objects.requireNonNull(keys.getKey()).longValue();
    }

    public boolean update(long id, String title, String body, String level, boolean published, Instant startAt,
                          Instant endAt) {
        return jdbc.update("""
                UPDATE announcement SET title = :title, body = :body, level = :level, status = :status,
                    start_at = :startAt, end_at = :endAt
                WHERE id = :id AND deleted_at IS NULL
                """, parameters(title, body, level, published, startAt, endAt).addValue("id", id)) == 1;
    }

    public boolean updateStatus(long id, boolean published) {
        return jdbc.update("UPDATE announcement SET status = :status WHERE id = :id AND deleted_at IS NULL",
                new MapSqlParameterSource("id", id).addValue("status", published ? 1 : 0)) == 1;
    }

    public boolean softDelete(long id) {
        return jdbc.update("UPDATE announcement SET deleted_at = UTC_TIMESTAMP(3) WHERE id = :id AND deleted_at IS NULL",
                new MapSqlParameterSource("id", id)) == 1;
    }

    private static MapSqlParameterSource parameters(String title, String body, String level, boolean published,
            Instant startAt, Instant endAt) {
        return new MapSqlParameterSource("title", title).addValue("body", body).addValue("level", level)
                .addValue("status", published ? 1 : 0)
                .addValue("startAt", startAt == null ? null : java.sql.Timestamp.from(startAt))
                .addValue("endAt", endAt == null ? null : java.sql.Timestamp.from(endAt));
    }

    private static AdminAnnouncementRow map(java.sql.ResultSet row, int index) throws java.sql.SQLException {
        Instant startAt = row.getTimestamp("start_at") == null ? null : row.getTimestamp("start_at").toInstant();
        Instant endAt = row.getTimestamp("end_at") == null ? null : row.getTimestamp("end_at").toInstant();
        boolean published = row.getInt("status") == 1;
        Instant now = Instant.now();
        boolean active = published && (startAt == null || !startAt.isAfter(now)) && (endAt == null || endAt.isAfter(now));
        return new AdminAnnouncementRow(row.getLong("id"), row.getString("title"), row.getString("body"),
                row.getString("level"), published, startAt, endAt, active,
                row.getTimestamp("created_at").toInstant(), row.getTimestamp("updated_at").toInstant());
    }
}
