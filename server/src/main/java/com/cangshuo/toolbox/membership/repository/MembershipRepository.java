package com.cangshuo.toolbox.membership.repository;

import com.cangshuo.toolbox.membership.model.MembershipRow;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MembershipRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public MembershipRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<MembershipRow> find(long userId) {
        return jdbc.query("SELECT plan, plan_expires_at FROM sys_user WHERE id = :id",
                new MapSqlParameterSource("id", userId), (row, index) -> {
                    Timestamp expires = row.getTimestamp("plan_expires_at");
                    return new MembershipRow(row.getString("plan"), expires == null ? null : expires.toInstant());
                }).stream().findFirst();
    }

    public boolean update(long userId, String plan, Instant expiresAt) {
        return jdbc.update("UPDATE sys_user SET plan = :plan, plan_expires_at = :expires,"
                        + " updated_at = UTC_TIMESTAMP(3) WHERE id = :id",
                new MapSqlParameterSource("id", userId).addValue("plan", plan)
                        .addValue("expires", expiresAt == null ? null : Timestamp.from(expiresAt))) == 1;
    }
}
