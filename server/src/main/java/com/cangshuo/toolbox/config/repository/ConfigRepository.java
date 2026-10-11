package com.cangshuo.toolbox.config.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ConfigRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ConfigRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, String> find(List<String> keys) {
        Map<String, String> values = new HashMap<>();
        jdbc.query("SELECT config_key, config_value FROM sys_config WHERE config_key IN (:keys)",
                new MapSqlParameterSource("keys", keys),
                row -> { values.put(row.getString("config_key"), row.getString("config_value")); });
        return values;
    }

    public boolean update(String key, String value) {
        return jdbc.update("INSERT INTO sys_config (config_key, config_value) VALUES (:key, :value)"
                        + " ON DUPLICATE KEY UPDATE config_value = :value",
                new MapSqlParameterSource("key", key).addValue("value", value)) >= 1;
    }
}
