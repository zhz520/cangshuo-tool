package com.cangshuo.toolbox.home.repository;

import com.cangshuo.toolbox.home.model.HomeAnnouncementResponse;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class HomeAnnouncementRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public HomeAnnouncementRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<HomeAnnouncementResponse> active() {
        return jdbc.query("""
                SELECT id, title, body, level FROM announcement
                WHERE deleted_at IS NULL AND status = 1
                  AND (start_at IS NULL OR start_at <= UTC_TIMESTAMP(3))
                  AND (end_at IS NULL OR end_at > UTC_TIMESTAMP(3))
                ORDER BY id DESC LIMIT 5
                """, new MapSqlParameterSource(), (row, index) -> new HomeAnnouncementResponse(
                row.getLong("id"), row.getString("title"), row.getString("body"), row.getString("level")));
    }
}
