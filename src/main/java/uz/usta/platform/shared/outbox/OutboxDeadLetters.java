package uz.usta.platform.shared.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Urinishlari tugagan ("dead") outbox xabarlari: ko'rish va sababi bartaraf etilgach qayta navbatga qo'yish. */
@Component
public class OutboxDeadLetters {

    private final JdbcClient jdbc;
    private final OutboxProperties props;

    OutboxDeadLetters(JdbcClient jdbc, OutboxProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    public record DeadEvent(UUID id, String aggregateType, UUID aggregateId, String eventType, int attempts,
                            String lastError, Instant createdAt) {
    }

    @Transactional(readOnly = true)
    public List<DeadEvent> list(int limit) {
        return jdbc.sql("""
                        SELECT id, aggregate_type, aggregate_id, event_type, attempts, last_error, created_at
                          FROM outbox_events
                         WHERE published_at IS NULL AND attempts >= :maxAttempts
                         ORDER BY created_at
                         LIMIT :limit""")
                .param("maxAttempts", props.maxAttempts()).param("limit", limit)
                .query((rs, i) -> new DeadEvent(rs.getObject("id", UUID.class), rs.getString("aggregate_type"),
                        rs.getObject("aggregate_id", UUID.class), rs.getString("event_type"), rs.getInt("attempts"),
                        rs.getString("last_error"), rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    /** @return qayta navbatga qo'yilgan xabarlar soni */
    @Transactional(propagation = Propagation.MANDATORY)
    public int requeueAll() {
        return jdbc.sql("""
                        UPDATE outbox_events SET attempts = 0, next_attempt_at = now()
                         WHERE published_at IS NULL AND attempts >= :maxAttempts""")
                .param("maxAttempts", props.maxAttempts())
                .update();
    }
}
