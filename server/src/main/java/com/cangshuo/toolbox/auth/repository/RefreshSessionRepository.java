package com.cangshuo.toolbox.auth.repository;

import com.cangshuo.toolbox.auth.model.RefreshSession;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class RefreshSessionRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public RefreshSessionRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public RefreshSession create(String code, long userId, Instant expires, Instant now) {
        var keys = new GeneratedKeyHolder();
        jdbc.update("INSERT INTO auth_refresh_session (session_code,user_id,expires_at,last_active_at) VALUES (:code,:user,:expires,:now)",
                new MapSqlParameterSource("code", code).addValue("user", userId)
                        .addValue("expires", Timestamp.from(expires)).addValue("now", Timestamp.from(now)), keys, new String[]{"id"});
        return new RefreshSession(java.util.Objects.requireNonNull(keys.getKey()).longValue(), code, userId, expires, null);
    }

    public Optional<RefreshSession> lock(String code) {
        return jdbc.query("SELECT id,session_code,user_id,expires_at,revoked_at FROM auth_refresh_session WHERE session_code=:code FOR UPDATE",
                new MapSqlParameterSource("code", code), (row, index) -> new RefreshSession(row.getLong("id"),
                        row.getString("session_code"), row.getLong("user_id"), row.getTimestamp("expires_at").toInstant(),
                        row.getTimestamp("revoked_at") == null ? null : row.getTimestamp("revoked_at").toInstant()))
                .stream().findFirst();
    }

    /** Missing is distinct from an unconsumed token. Never revoke on a guessed token. */
    public Optional<Boolean> consumed(long sessionId, String hash) {
        return jdbc.query("SELECT consumed_at IS NOT NULL FROM auth_refresh_token WHERE session_id=:id AND token_hash=:hash",
                new MapSqlParameterSource("id", sessionId).addValue("hash", hash), (row, index) -> row.getBoolean(1))
                .stream().findFirst();
    }

    public void insertToken(long sessionId, String hash) {
        jdbc.update("INSERT INTO auth_refresh_token (session_id,token_hash) VALUES (:id,:hash)",
                new MapSqlParameterSource("id", sessionId).addValue("hash", hash));
    }

    public void consume(long sessionId, String hash, Instant now) {
        jdbc.update("UPDATE auth_refresh_token SET consumed_at=:now WHERE session_id=:id AND token_hash=:hash",
                new MapSqlParameterSource("id", sessionId).addValue("hash", hash).addValue("now", Timestamp.from(now)));
        jdbc.update("UPDATE auth_refresh_session SET last_active_at=:now WHERE id=:id",
                new MapSqlParameterSource("id", sessionId).addValue("now", Timestamp.from(now)));
    }

    public void revoke(long sessionId, Instant now) {
        jdbc.update("UPDATE auth_refresh_session SET revoked_at=COALESCE(revoked_at,:now) WHERE id=:id",
                new MapSqlParameterSource("id", sessionId).addValue("now", Timestamp.from(now)));
    }

    public boolean active(String code, long userId, Instant now) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM auth_refresh_session s JOIN sys_user u ON u.id=s.user_id
                WHERE s.session_code=:code AND s.user_id=:user AND s.revoked_at IS NULL AND s.expires_at>:now AND u.status=1)
                """, new MapSqlParameterSource("code", code).addValue("user", userId).addValue("now", Timestamp.from(now)), Boolean.class));
    }

    public int deleteExpired(Instant now) {
        return jdbc.update("DELETE FROM auth_refresh_session WHERE expires_at <= :now LIMIT 1000",
                new MapSqlParameterSource("now", Timestamp.from(now)));
    }
}
