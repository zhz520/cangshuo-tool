package com.cangshuo.toolbox.feedback.repository;

import com.cangshuo.toolbox.feedback.model.FeedbackResponse;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class FeedbackRepository {
    private static final String USER_COLUMNS = """
            SELECT id, type, content, contact, status, reply, replied_at, created_at, updated_at
            FROM user_feedback
            """;

    private final NamedParameterJdbcTemplate jdbc;
    public FeedbackRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public long create(long userId, String type, String content, String contact) {
        var keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO user_feedback (user_id, type, content, contact)
                VALUES (:userId, :type, :content, :contact)
                """, new MapSqlParameterSource("userId", userId).addValue("type", type)
                .addValue("content", content).addValue("contact", contact), keys, new String[]{"id"});
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    public List<FeedbackResponse> listByUser(long userId) {
        return jdbc.query(USER_COLUMNS + " WHERE user_id = :userId ORDER BY id DESC LIMIT 50",
                new MapSqlParameterSource("userId", userId), FeedbackRepository::mapUser);
    }

    public Optional<FeedbackResponse> findById(long userId, long id) {
        return jdbc.query(USER_COLUMNS + " WHERE user_id = :userId AND id = :id",
                new MapSqlParameterSource("userId", userId).addValue("id", id), FeedbackRepository::mapUser)
                .stream().findFirst();
    }

    public long countForAdmin(String keyword, String status) {
        return Objects.requireNonNull(jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_feedback f JOIN sys_user u ON u.id = f.user_id" + adminFilters(keyword, status),
                adminParameters(keyword, status), Long.class));
    }

    public List<AdminFeedbackRowView> findForAdmin(String keyword, String status, int limit, long offset) {
        var params = adminParameters(keyword, status).addValue("limit", limit).addValue("offset", offset);
        return jdbc.query("""
                SELECT f.id, f.user_id, u.email, f.type, f.content, f.contact, f.status, f.reply, f.replied_at,
                       f.created_at, f.updated_at
                FROM user_feedback f JOIN sys_user u ON u.id = f.user_id
                """ + adminFilters(keyword, status) + " ORDER BY f.id DESC LIMIT :limit OFFSET :offset",
                params, (row, index) -> new AdminFeedbackRowView(row.getLong("id"), row.getLong("user_id"),
                        row.getString("email"), row.getString("type"), row.getString("content"),
                        row.getString("contact"), row.getString("status"), row.getString("reply"),
                        instant(row, "replied_at"), instant(row, "created_at"), instant(row, "updated_at")));
    }

    public Optional<Long> findOwner(long id) {
        return jdbc.query("SELECT user_id FROM user_feedback WHERE id = :id", new MapSqlParameterSource("id", id),
                (row, index) -> row.getLong("user_id")).stream().findFirst();
    }

    public Optional<AdminFeedbackRowView> findForAdminById(long id) {
        return jdbc.query("""
                SELECT f.id, f.user_id, u.email, f.type, f.content, f.contact, f.status, f.reply, f.replied_at,
                       f.created_at, f.updated_at
                FROM user_feedback f JOIN sys_user u ON u.id = f.user_id
                WHERE f.id = :id
                """, new MapSqlParameterSource("id", id), (row, index) -> new AdminFeedbackRowView(
                row.getLong("id"), row.getLong("user_id"), row.getString("email"), row.getString("type"),
                row.getString("content"), row.getString("contact"), row.getString("status"), row.getString("reply"),
                instant(row, "replied_at"), instant(row, "created_at"), instant(row, "updated_at")))
                .stream().findFirst();
    }

    public boolean update(long id, String status, String reply) {
        return jdbc.update("""
                UPDATE user_feedback SET status = :status,
                    reply = CASE WHEN :hasReply THEN :reply ELSE reply END,
                    replied_at = CASE WHEN :hasReply THEN UTC_TIMESTAMP(3) ELSE replied_at END
                WHERE id = :id
                """, new MapSqlParameterSource("id", id).addValue("status", status).addValue("reply", reply)
                .addValue("hasReply", reply != null)) == 1;
    }

    /** Internal admin projection; kept inside the repository package on purpose. */
    public record AdminFeedbackRowView(long id, long userId, String userEmail, String type, String content,
        String contact, String status, String reply, Instant repliedAt, Instant createdAt, Instant updatedAt) { }

    private static String adminFilters(String keyword, String status) {
        return " WHERE 1 = 1"
                + (keyword == null ? "" : " AND (LOCATE(:keyword, f.content) > 0 OR LOCATE(:keyword, f.contact) > 0"
                        + " OR LOCATE(:keyword, u.email) > 0)")
                + (status == null ? "" : " AND f.status = :status");
    }

    private static MapSqlParameterSource adminParameters(String keyword, String status) {
        return new MapSqlParameterSource("keyword", keyword).addValue("status", status);
    }

    private static FeedbackResponse mapUser(java.sql.ResultSet row, int index) throws java.sql.SQLException {
        return new FeedbackResponse(row.getLong("id"), row.getString("type"), row.getString("content"),
                row.getString("contact"), row.getString("status"), row.getString("reply"),
                instant(row, "replied_at"), instant(row, "created_at"), instant(row, "updated_at"));
    }

    private static Instant instant(java.sql.ResultSet row, String column) throws java.sql.SQLException {
        java.sql.Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
