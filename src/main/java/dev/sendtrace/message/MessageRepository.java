package dev.sendtrace.message;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MessageRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public MessageRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, UUID tenantId, Channel channel, String recipient, String body,
                        MessageStatus status, Instant now) {
        jdbc.update("""
            INSERT INTO messages (id, tenant_id, channel, recipient, body, status, created_at, updated_at)
            VALUES (:id, :tenantId, :channel, :recipient, :body, :status, :now, :now)
            """, new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("tenantId", tenantId)
            .addValue("channel", channel.name())
            .addValue("recipient", recipient)
            .addValue("body", body)
            .addValue("status", status.name())
            .addValue("now", Timestamp.from(now)));
    }

    public Optional<MessageView> find(UUID tenantId, UUID id) {
        return jdbc.query("""
            SELECT id, channel, recipient, status, created_at
            FROM messages
            WHERE tenant_id = :tenantId AND id = :id
            """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", id),
            (rs, n) -> new MessageView(
                UUID.fromString(rs.getString("id")),
                Channel.valueOf(rs.getString("channel")),
                rs.getString("recipient"),
                MessageStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant()))
            .stream().findFirst();
    }
}
