package com.cangshuo.toolbox.admin.repository;

import com.cangshuo.toolbox.admin.model.AdminAccount;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class AdminAccountRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public AdminAccountRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String SELECT = "SELECT id, username, password_hash, nickname, role_code, status FROM admin_user";

    public Optional<AdminAccount> findByUsername(String username) {
        return jdbc.query(SELECT + " WHERE username = :username", new MapSqlParameterSource("username", username), AdminAccountRepository::map)
                .stream().findFirst();
    }

    public Optional<AdminAccount> findById(long id) {
        return jdbc.query(SELECT + " WHERE id = :id", new MapSqlParameterSource("id", id), AdminAccountRepository::map)
                .stream().findFirst();
    }

    public boolean existsAny() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM admin_user", new MapSqlParameterSource(), Integer.class);
        return count != null && count > 0;
    }

    public AdminAccount create(String username, String hash, String nickname, String roleCode) {
        var keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO admin_user (username, password_hash, nickname, role_code, last_login_at)
                VALUES (:username, :hash, :nickname, :roleCode, NULL)
                """, new MapSqlParameterSource("username", username).addValue("hash", hash)
                .addValue("nickname", nickname).addValue("roleCode", roleCode), keys, new String[]{"id"});
        return new AdminAccount(Objects.requireNonNull(keys.getKey()).longValue(), username, hash, nickname, roleCode, 1);
    }

    public void recordLogin(long id) {
        jdbc.update("UPDATE admin_user SET last_login_at = UTC_TIMESTAMP(3) WHERE id = :id", new MapSqlParameterSource("id", id));
    }

    private static AdminAccount map(java.sql.ResultSet row, int index) throws java.sql.SQLException {
        return new AdminAccount(row.getLong("id"), row.getString("username"), row.getString("password_hash"),
                row.getString("nickname"), row.getString("role_code"), row.getInt("status"));
    }
}
