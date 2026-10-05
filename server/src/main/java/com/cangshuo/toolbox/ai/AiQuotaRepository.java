package com.cangshuo.toolbox.ai;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AiQuotaRepository {
    private final JdbcTemplate jdbc;
    public AiQuotaRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    /** Atomic conditional increment across instances; provider failures still consume an attempt. */
    public boolean reserve(long user, int limit) {
        var day = Date.valueOf(LocalDate.now(ZoneOffset.UTC));
        jdbc.update("INSERT IGNORE INTO ai_daily_usage(user_id, usage_date, attempts) VALUES (?, ?, 0)", user, day);
        return jdbc.update("UPDATE ai_daily_usage SET attempts = attempts + 1 WHERE user_id = ? AND usage_date = ? AND attempts < ?", user, day, limit) == 1;
    }
    public int used(long user) {
        var values = jdbc.queryForList("SELECT attempts FROM ai_daily_usage WHERE user_id = ? AND usage_date = ?",
            Integer.class, user, Date.valueOf(LocalDate.now(ZoneOffset.UTC)));
        return values.isEmpty() ? 0 : values.get(0);
    }
}
