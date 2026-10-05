package com.cangshuo.toolbox.sync.repository;

import com.cangshuo.toolbox.sync.model.SyncModels.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SyncRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    public SyncRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper json) { this.jdbc=jdbc; this.json=json; }
    public boolean lockUser(long user) {
        return !jdbc.queryForList("SELECT id FROM sys_user WHERE id=:u AND status=1 FOR UPDATE", p(user)).isEmpty();
    }
    public long revision(long user) {
        return jdbc.query("SELECT revision FROM user_sync_state WHERE user_id=:u",p(user),
                (r,i)->r.getLong(1)).stream().findFirst().orElse(0L);
    }
    public Optional<Entry> find(long user, String type, String key) {
        return jdbc.query("SELECT * FROM user_sync_entity WHERE user_id=:u AND entity_type=:t AND entity_key=:k",
                p(user).addValue("t",type).addValue("k",key),this::row).stream().findFirst();
    }
    public long count(long user) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user_sync_entity WHERE user_id=:u",p(user),Long.class);
    }
    public List<Entry> pull(long user, long after) {
        return jdbc.query("SELECT * FROM user_sync_entity WHERE user_id=:u AND revision>:r ORDER BY revision LIMIT 101",
                p(user).addValue("r",after),this::row);
    }
    public void save(long user, Entry e) {
        jdbc.update("""
            INSERT INTO user_sync_entity(user_id,entity_type,entity_key,updated_at_ms,device_id,deleted,payload_json,revision)
            VALUES(:u,:t,:k,:at,:d,:x,:j,:r)
            ON DUPLICATE KEY UPDATE updated_at_ms=:at,device_id=:d,deleted=:x,payload_json=:j,revision=:r
            """,p(user).addValue("t",e.entityType()).addValue("k",e.entityKey()).addValue("at",e.updatedAt())
                .addValue("d",e.deviceId()).addValue("x",e.deleted()).addValue("j",encode(e.payload())).addValue("r",e.revision()));
    }
    public void revision(long user, long revision) {
        jdbc.update("INSERT INTO user_sync_state(user_id,revision) VALUES(:u,:r) ON DUPLICATE KEY UPDATE revision=:r",
                p(user).addValue("r",revision));
    }
    private MapSqlParameterSource p(long user) { return new MapSqlParameterSource("u",user); }
    private Entry row(java.sql.ResultSet r,int index) throws java.sql.SQLException {
        try {
            return new Entry(r.getString("entity_type"),r.getString("entity_key"),r.getLong("updated_at_ms"),
                    r.getString("device_id"),r.getBoolean("deleted"),json.readValue(r.getString("payload_json"),Payload.class),r.getLong("revision"));
        } catch (JsonProcessingException e) { throw new IllegalStateException("Invalid stored sync payload"); }
    }
    private String encode(Payload payload) {
        try { return json.writeValueAsString(payload); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Invalid sync payload"); }
    }
}
