package com.cangshuo.toolbox.home.repository;

import com.cangshuo.toolbox.home.model.HomeRecommendationResponse;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class HomeRecommendationRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public HomeRecommendationRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<HomeRecommendationResponse> active() {
        return jdbc.query("""
                SELECT slot_code, title, subtitle, tool_code, link_url, image_url
                FROM home_recommendation
                WHERE deleted_at IS NULL AND status = 1
                  AND (start_at IS NULL OR start_at <= UTC_TIMESTAMP(3))
                  AND (end_at IS NULL OR end_at > UTC_TIMESTAMP(3))
                ORDER BY sort_order ASC, id ASC
                LIMIT 20
                """, new MapSqlParameterSource(), (row, index) -> new HomeRecommendationResponse(
                row.getString("slot_code"), row.getString("title"), row.getString("subtitle"),
                row.getString("tool_code"), row.getString("link_url"), row.getString("image_url")));
    }
}
