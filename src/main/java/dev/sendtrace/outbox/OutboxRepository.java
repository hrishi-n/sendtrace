package dev.sendtrace.outbox;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class OutboxRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public OutboxRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, UUID tenantId, String aggregateType, UUID aggregateId, String eventType,
                        String payloadJson, Instant now) {
        jdbc.update("""
            INSERT INTO outbox_events (id, tenant_id, aggregate_type, aggregate_id, event_type, payload, created_at)
            VALUES (:id, :tenantId, :aggregateType, :aggregateId, :eventType, CAST(:payload AS jsonb), :now)
            """, new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("tenantId", tenantId)
            .addValue("aggregateType", aggregateType)
            .addValue("aggregateId", aggregateId)
            .addValue("eventType", eventType)
            .addValue("payload", payloadJson)
            .addValue("now", Timestamp.from(now)));
    }

    // Locks a batch of unpublished rows so concurrent pollers never publish the same row twice.
    public List<OutboxRow> claimBatch(int limit) {
        return jdbc.query("""
            SELECT id, tenant_id, event_type, payload::text AS payload
            FROM outbox_events
            WHERE published_at IS NULL
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, new MapSqlParameterSource().addValue("limit", limit),
            (rs, n) -> new OutboxRow(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("tenant_id")),
                rs.getString("event_type"),
                rs.getString("payload")));
    }

    public void markPublished(UUID id, Instant now) {
        jdbc.update("UPDATE outbox_events SET published_at = :now WHERE id = :id",
            new MapSqlParameterSource().addValue("now", Timestamp.from(now)).addValue("id", id));
    }

    public void incrementAttempts(UUID id) {
        jdbc.update("UPDATE outbox_events SET attempts = attempts + 1 WHERE id = :id",
            new MapSqlParameterSource().addValue("id", id));
    }
}
