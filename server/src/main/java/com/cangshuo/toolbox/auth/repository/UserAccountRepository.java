package com.cangshuo.toolbox.auth.repository;

import com.cangshuo.toolbox.auth.model.UserAccount;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class UserAccountRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public UserAccountRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<UserAccount> findByEmail(String email) {
        return jdbc.query("SELECT id, email, password_hash, nickname, status FROM sys_user WHERE email = :email",
                new MapSqlParameterSource("email", email), (row, index) -> new UserAccount(row.getLong("id"),
                        row.getString("email"), row.getString("password_hash"), row.getString("nickname"),
                        row.getInt("status"))).stream().findFirst();
    }

    public Optional<UserAccount> findById(long id) {
        return jdbc.query("SELECT id, email, password_hash, nickname, status FROM sys_user WHERE id = :id",
                new MapSqlParameterSource("id", id), (row, index) -> new UserAccount(row.getLong("id"),
                        row.getString("email"), row.getString("password_hash"), row.getString("nickname"),
                        row.getInt("status"))).stream().findFirst();
    }

    public UserAccount create(String email, String hash, String nickname) {
        var keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO sys_user (email, password_hash, nickname, last_login_at)
                VALUES (:email, :hash, :nickname, UTC_TIMESTAMP(3))
                """, new MapSqlParameterSource("email", email).addValue("hash", hash)
                .addValue("nickname", nickname), keys, new String[]{"id"});
        return new UserAccount(java.util.Objects.requireNonNull(keys.getKey()).longValue(), email, hash, nickname, 1);
    }

    public void recordLogin(long id) {
        jdbc.update("UPDATE sys_user SET last_login_at = UTC_TIMESTAMP(3) WHERE id = :id",
                new MapSqlParameterSource("id", id));
    }

    public boolean updateNickname(long id, String nickname) {
        return jdbc.update("UPDATE sys_user SET nickname = :nickname, updated_at = UTC_TIMESTAMP(3) WHERE id = :id AND status = 1",
                new MapSqlParameterSource("id", id).addValue("nickname", nickname)) == 1;
    }
}
