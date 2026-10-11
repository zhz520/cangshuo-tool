package com.cangshuo.toolbox.statistics.repository;

import com.cangshuo.toolbox.statistics.model.StatisticsResponse.Distribution;
import com.cangshuo.toolbox.statistics.model.StatisticsResponse.Rank;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StatisticsRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public StatisticsRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Instant collectionSince() {
        return jdbc.queryForObject("SELECT started_at FROM analytics_collection WHERE id = 1",
                Map.of(), (rs, i) -> rs.getTimestamp(1).toInstant());
    }

    public Map<String, Long> summary(LocalDate from, LocalDate today, Instant start, Instant end, Instant now) {
        var p = new MapSqlParameterSource("from", from).addValue("today", today)
                .addValue("start", Timestamp.from(start)).addValue("end", Timestamp.from(end))
                .addValue("now", Timestamp.from(now)).addValue("week", today.minusDays(6));
        var counts = new LinkedHashMap<String, Long>();
        readCounts(counts, """
                SELECT COUNT(*) AS users, COALESCE(SUM(status = 1), 0) AS enabledUsers,
                  COALESCE(SUM(status = 0), 0) AS disabledUsers,
                  COALESCE(SUM(status = 1 AND plan = 'PRO' AND
                    (plan_expires_at IS NULL OR plan_expires_at > :now)), 0) AS activeMembers,
                  COALESCE(SUM(plan = 'PRO' AND plan_expires_at <= :now), 0) AS expiredMembers,
                  COALESCE(SUM(created_at >= :start AND created_at < :end), 0) AS newUsers
                FROM sys_user
                """, p);
        readCounts(counts, """
                SELECT COUNT(*) AS tools, COALESCE(SUM(status = 'ENABLED'), 0) AS enabledTools,
                  COALESCE(SUM(status = 'DISABLED'), 0) AS disabledTools,
                  COALESCE(SUM(status = 'MAINTENANCE'), 0) AS maintenanceTools
                FROM tool_definition WHERE deleted_at IS NULL
                """, p);
        readCounts(counts, """
                SELECT COUNT(*) AS feedbackTotal, COALESCE(SUM(status = 'PENDING'), 0) AS pendingFeedback,
                  COALESCE(SUM(status = 'PROCESSING'), 0) AS processingFeedback,
                  COALESCE(SUM(status = 'RESOLVED'), 0) AS resolvedFeedback
                FROM user_feedback
                """, p);
        readCounts(counts, """
                SELECT COUNT(*) AS sites, COALESCE(SUM(status = 'PENDING'), 0) AS pendingSites,
                  COALESCE(SUM(status = 'APPROVED'), 0) AS approvedSites,
                  COALESCE(SUM(status = 'REJECTED'), 0) AS rejectedSites FROM site_link
                """, p);
        readCounts(counts, """
                SELECT COUNT(*) AS storedFiles, COALESCE(SUM(size_bytes), 0) AS storedBytes
                FROM stored_object WHERE deleted_at IS NULL
                """, p);
        readCounts(counts, """
                SELECT COALESCE(SUM(entity_type = 'FAVORITE'), 0) AS syncedFavorites,
                  COALESCE(SUM(entity_type = 'RECENT'), 0) AS syncedRecents
                FROM user_sync_entity WHERE deleted = 0
                """, p);
        readCounts(counts, "SELECT COALESCE(SUM(use_count), 0) AS allTimeOpens FROM tool_usage_stat", p);
        readCounts(counts, """
                SELECT COALESCE(SUM(use_count), 0) AS windowOpens,
                  COALESCE(SUM(CASE WHEN usage_date = :today THEN use_count ELSE 0 END), 0) AS todayOpens
                FROM tool_usage_daily WHERE usage_date >= :from AND usage_date <= :today
                """, p);
        readCounts(counts, """
                SELECT COUNT(DISTINCT user_id) AS activeUsers,
                  COUNT(DISTINCT CASE WHEN activity_date = :today THEN user_id END) AS dailyActiveUsers,
                  COUNT(DISTINCT CASE WHEN activity_date >= :week THEN user_id END) AS weeklyActiveUsers
                FROM user_activity_daily WHERE activity_date >= :from AND activity_date <= :today
                """, p);
        // AI quota rows are UTC calendar days; keep this explicit in the response/UI documentation.
        readCounts(counts, """
                SELECT COALESCE(SUM(attempts), 0) AS aiAttempts
                FROM ai_daily_usage WHERE usage_date >= :from AND usage_date <= :today
                """, p);
        return Map.copyOf(counts);
    }

    private void readCounts(Map<String, Long> target, String sql, MapSqlParameterSource params) {
        jdbc.query(sql, params, rs -> {
            var meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) target.put(meta.getColumnLabel(i), rs.getLong(i));
        });
    }

    public Map<LocalDate, Long> datedRegistrations(Instant start, Instant end) {
        return dated("""
                SELECT DATE(created_at + INTERVAL 8 HOUR) AS day, COUNT(*) AS n
                FROM sys_user WHERE created_at >= :start AND created_at < :end GROUP BY day
                """, start, end);
    }
    public Map<LocalDate, Long> datedFeedback(Instant start, Instant end) {
        return dated("""
                SELECT DATE(created_at + INTERVAL 8 HOUR) AS day, COUNT(*) AS n
                FROM user_feedback WHERE created_at >= :start AND created_at < :end GROUP BY day
                """, start, end);
    }
    public Map<LocalDate, Long> datedSubmissions(Instant start, Instant end) {
        return dated("""
                SELECT DATE(created_at + INTERVAL 8 HOUR) AS day, COUNT(*) AS n
                FROM site_link WHERE created_at >= :start AND created_at < :end GROUP BY day
                """, start, end);
    }
    private Map<LocalDate, Long> dated(String sql, Instant start, Instant end) {
        var result = new LinkedHashMap<LocalDate, Long>();
        jdbc.query(sql, new MapSqlParameterSource("start", Timestamp.from(start)).addValue("end", Timestamp.from(end)),
                rs -> { result.put(rs.getDate("day").toLocalDate(), rs.getLong("n")); });
        return result;
    }
    public Map<LocalDate, Long> datedOpens(LocalDate from, LocalDate to) {
        var result = new LinkedHashMap<LocalDate, Long>();
        jdbc.query("""
                SELECT usage_date, SUM(use_count) AS n FROM tool_usage_daily
                WHERE usage_date >= :from AND usage_date <= :to GROUP BY usage_date
                """, dates(from, to), rs -> { result.put(rs.getDate(1).toLocalDate(), rs.getLong(2)); });
        return result;
    }
    public Map<LocalDate, Long> datedActivity(LocalDate from, LocalDate to) {
        var result = new LinkedHashMap<LocalDate, Long>();
        jdbc.query("""
                SELECT activity_date, COUNT(*) AS n FROM user_activity_daily
                WHERE activity_date >= :from AND activity_date <= :to GROUP BY activity_date
                """, dates(from, to), rs -> { result.put(rs.getDate(1).toLocalDate(), rs.getLong(2)); });
        return result;
    }
    public List<Rank> ranking(LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT t.tool_code, t.name, c.name AS category, t.status,
                  COALESCE(d.opens, 0) AS opens, COALESCE(u.use_count, 0) AS all_time
                FROM tool_definition t JOIN tool_category c ON c.id = t.category_id
                LEFT JOIN tool_usage_stat u ON u.tool_code = t.tool_code
                LEFT JOIN (SELECT tool_code, SUM(use_count) AS opens FROM tool_usage_daily
                  WHERE usage_date >= :from AND usage_date <= :to GROUP BY tool_code) d ON d.tool_code = t.tool_code
                WHERE t.deleted_at IS NULL
                ORDER BY opens DESC, all_time DESC, t.tool_code ASC LIMIT 20
                """, dates(from, to), (rs, i) -> new Rank(rs.getString("tool_code"), rs.getString("name"),
                    rs.getString("category"), rs.getString("status"), rs.getLong("opens"), rs.getLong("all_time")));
    }
    public List<Distribution> categories() {
        return jdbc.query("""
                SELECT c.code, c.name, COUNT(t.id) AS n, COALESCE(SUM(u.use_count), 0) AS opens
                FROM tool_category c LEFT JOIN tool_definition t ON t.category_id = c.id AND t.deleted_at IS NULL
                LEFT JOIN tool_usage_stat u ON u.tool_code = t.tool_code
                WHERE c.deleted_at IS NULL GROUP BY c.id, c.code, c.name ORDER BY n DESC, c.code
                """, Map.of(), (rs, i) -> new Distribution(rs.getString("code"), rs.getString("name"),
                    rs.getLong("n"), rs.getLong("opens")));
    }
    public List<Distribution> modes() {
        return jdbc.query("""
                SELECT mode, COUNT(*) AS n, COALESCE(SUM(u.use_count), 0) AS opens
                FROM tool_definition t LEFT JOIN tool_usage_stat u ON u.tool_code = t.tool_code
                WHERE t.deleted_at IS NULL GROUP BY mode ORDER BY n DESC, mode
                """, Map.of(), (rs, i) -> new Distribution(rs.getString("mode"), rs.getString("mode"),
                    rs.getLong("n"), rs.getLong("opens")));
    }
    public void recordActivity(long user, LocalDate day) {
        jdbc.update("INSERT IGNORE INTO user_activity_daily (user_id, activity_date) VALUES (:user, :day)",
                new MapSqlParameterSource("user", user).addValue("day", day));
    }
    private MapSqlParameterSource dates(LocalDate from, LocalDate to) {
        return new MapSqlParameterSource("from", from).addValue("to", to);
    }
}
