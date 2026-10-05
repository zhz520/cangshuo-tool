package com.cangshuo.toolbox.storage;

import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StoredObjectRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public StoredObjectRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Usage(long bytes, long objects) { }

    public record StoredObject(long id, String ownerType, long ownerId, String key, long size, String sha256,
        String contentType) { }

    public Optional<StoredObject> findActive(String key) {
        return jdbc.query("""
                SELECT id, owner_type, owner_id, object_key, size_bytes, sha256, content_type
                FROM stored_object WHERE object_key = :key AND deleted_at IS NULL
                """, new MapSqlParameterSource("key", key), (row, index) -> new StoredObject(row.getLong("id"),
                row.getString("owner_type"), row.getLong("owner_id"), row.getString("object_key"),
                row.getLong("size_bytes"), row.getString("sha256"), row.getString("content_type")))
                .stream().findFirst();
    }

    public void insert(String ownerType, long ownerId, String key, long size, String sha256, String contentType) {
        jdbc.update("""
                INSERT INTO stored_object (owner_type, owner_id, object_key, size_bytes, sha256, content_type)
                VALUES (:ownerType, :ownerId, :key, :size, :sha256, :contentType)
                """, new MapSqlParameterSource("ownerType", ownerType).addValue("ownerId", ownerId)
                .addValue("key", key).addValue("size", size).addValue("sha256", sha256)
                .addValue("contentType", contentType));
    }

    public boolean softDelete(String key) {
        return jdbc.update(
                "UPDATE stored_object SET deleted_at = UTC_TIMESTAMP(3) WHERE object_key = :key AND deleted_at IS NULL",
                new MapSqlParameterSource("key", key)) == 1;
    }

    public Usage usage(String ownerType, long ownerId) {
        return Objects.requireNonNull(jdbc.queryForObject("""
                SELECT COUNT(*) AS object_count, COALESCE(SUM(size_bytes), 0) AS total_bytes
                FROM stored_object WHERE owner_type = :ownerType AND owner_id = :ownerId AND deleted_at IS NULL
                """, new MapSqlParameterSource("ownerType", ownerType).addValue("ownerId", ownerId),
                (row, index) -> new Usage(row.getLong("total_bytes"), row.getLong("object_count"))));
    }

    public long totalBytes() {
        Long total = jdbc.queryForObject(
                "SELECT COALESCE(SUM(size_bytes), 0) FROM stored_object WHERE deleted_at IS NULL",
                new MapSqlParameterSource(), Long.class);
        return total == null ? 0L : total;
    }
}
