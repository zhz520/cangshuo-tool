package com.cangshuo.toolbox.sitelink.repository;

import com.cangshuo.toolbox.sitelink.model.SiteLinkRow;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class SiteLinkRepository {

    private static final String COLUMNS = "id, title, url, description, status, submitted_by, created_at, updated_at";

    private final NamedParameterJdbcTemplate jdbc;

    public SiteLinkRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SiteLinkRow> findApproved(int limit) {
        return jdbc.query("SELECT " + COLUMNS + " FROM site_link WHERE status = 'APPROVED'"
                        + " ORDER BY updated_at DESC, id DESC LIMIT :limit",
                new MapSqlParameterSource("limit", limit), SiteLinkRepository::map);
    }

    public long insert(String title, String url, String description, Long submittedBy) {
        var holder = new GeneratedKeyHolder();
        jdbc.update("INSERT INTO site_link (title, url, description, status, submitted_by)"
                        + " VALUES (:title, :url, :description, 'PENDING', :submittedBy)",
                new MapSqlParameterSource("title", title).addValue("url", url)
                        .addValue("description", description).addValue("submittedBy", submittedBy), holder);
        return Objects.requireNonNull(holder.getKey()).longValue();
    }

    public long count(String status, String keyword) {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM site_link " + filter(status, keyword),
                filterParams(status, keyword), Long.class));
    }

    public List<SiteLinkRow> page(String status, String keyword, int limit, long offset) {
        return jdbc.query("SELECT " + COLUMNS + " FROM site_link " + filter(status, keyword)
                        + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset",
                filterParams(status, keyword).addValue("limit", limit).addValue("offset", offset),
                SiteLinkRepository::map);
    }

    public boolean updateStatus(long id, String status) {
        return jdbc.update("UPDATE site_link SET status = :status WHERE id = :id",
                new MapSqlParameterSource("id", id).addValue("status", status)) == 1;
    }

    public SiteLinkRow find(long id) {
        List<SiteLinkRow> rows = jdbc.query("SELECT " + COLUMNS + " FROM site_link WHERE id = :id",
                new MapSqlParameterSource("id", id), SiteLinkRepository::map);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static String filter(String status, String keyword) {
        StringBuilder sql = new StringBuilder("WHERE 1 = 1");
        if (status != null) sql.append(" AND status = :status");
        if (keyword != null) sql.append(" AND (title LIKE :like OR url LIKE :like OR description LIKE :like)");
        return sql.toString();
    }

    private static MapSqlParameterSource filterParams(String status, String keyword) {
        return new MapSqlParameterSource().addValue("status", status)
                .addValue("like", keyword == null ? null : "%" + keyword + "%");
    }

    private static SiteLinkRow map(java.sql.ResultSet row, int rowNumber) throws java.sql.SQLException {
        long submittedRaw = row.getLong("submitted_by");
        Long submittedBy = row.wasNull() ? null : submittedRaw;
        return new SiteLinkRow(row.getLong("id"), row.getString("title"), row.getString("url"),
                row.getString("description"), row.getString("status"),
                submittedBy,
                row.getTimestamp("created_at").toInstant(), row.getTimestamp("updated_at").toInstant());
    }
}
