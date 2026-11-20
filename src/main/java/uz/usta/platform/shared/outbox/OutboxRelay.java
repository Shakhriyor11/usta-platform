package uz.usta.platform.shared.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox'dagi yetkazilmagan xabarlarni o'qib, {@link OutboxDispatcher} orqali yetkazadi.
 *
 * <ul>
 *   <li>Har bir xabar alohida tranzaksiyada qayta ishlanadi; {@code FOR UPDATE SKIP LOCKED} tufayli
 *       bir nechta instansiya parallel ishlay oladi va bitta xabar ikki marta olinmaydi.</li>
 *   <li>Xatolikda {@code attempts} oshiriladi va {@code next_attempt_at} chiziqli backoff bilan
 *       suriladi; {@code maxAttempts} ga yetgan xabar "dead" bo'lib qoladi (metrika orqali kuzatiladi).</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "app.outbox.relay-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int MAX_ERROR_LENGTH = 2000;

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final OutboxDispatcher dispatcher;
    private final OutboxProperties props;
    private final Clock clock;
    private final Counter published;
    private final Counter failed;

    OutboxRelay(JdbcClient jdbc, TransactionTemplate tx, OutboxDispatcher dispatcher, OutboxProperties props,
                Clock clock, MeterRegistry registry) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.dispatcher = dispatcher;
        this.props = props;
        this.clock = clock;
        this.published = Counter.builder("outbox.events.published").register(registry);
        this.failed = Counter.builder("outbox.events.failed").register(registry);
        Gauge.builder("outbox.events.pending", this, OutboxRelay::pendingCount).register(registry);
        Gauge.builder("outbox.events.dead", this, OutboxRelay::deadCount).register(registry);
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval:PT2S}")
    public void scheduledRelay() {
        relayPending();
    }

    /** @return yetkazilgan xabarlar soni */
    public int relayPending() {
        int delivered = 0;
        Set<UUID> failedInThisPass = new HashSet<>();
        for (int i = 0; i < props.batchSize(); i++) {
            Attempt attempt = tx.execute(status -> {
                Optional<OutboxMessage> next = fetchNext(failedInThisPass);
                if (next.isEmpty()) {
                    return null;
                }
                OutboxMessage m = next.get();
                try {
                    dispatcher.dispatch(m);
                    markPublished(m.id());
                    return new Attempt(m, null);
                } catch (RuntimeException e) {
                    status.setRollbackOnly();
                    return new Attempt(m, e);
                }
            });
            if (attempt == null) {
                break;
            }
            if (attempt.error() == null) {
                published.increment();
                delivered++;
            } else {
                failedInThisPass.add(attempt.message().id());
                recordFailure(attempt.message(), attempt.error());
            }
        }
        return delivered;
    }

    @Scheduled(cron = "${app.outbox.cleanup-cron:0 30 3 * * *}")
    @SchedulerLock(name = "outbox-cleanup", lockAtMostFor = "PT10M")
    public void cleanup() {
        Instant threshold = Instant.now(clock).minus(props.retention());
        int removed = jdbc.sql("DELETE FROM outbox_events WHERE published_at IS NOT NULL AND published_at < :t")
                .param("t", Timestamp.from(threshold))
                .update();
        if (removed > 0) {
            log.info("Outbox cleanup: {} published events removed", removed);
        }
    }

    /** @param exclude shu siklda yiqilgan xabarlar — ular keyingi siklgacha qayta olinmaydi */
    private Optional<OutboxMessage> fetchNext(Set<UUID> exclude) {
        String excludeClause = exclude.isEmpty() ? "" : " AND id NOT IN (:exclude)";
        var spec = jdbc.sql("""
                        SELECT id, aggregate_type, aggregate_id, event_type, payload::text AS payload, created_at, attempts
                          FROM outbox_events
                         WHERE published_at IS NULL
                           AND attempts < :maxAttempts
                           AND next_attempt_at <= now()""" + excludeClause + """
                         ORDER BY next_attempt_at, id
                         LIMIT 1
                           FOR UPDATE SKIP LOCKED
                        """)
                .param("maxAttempts", props.maxAttempts());
        if (!exclude.isEmpty()) {
            spec = spec.param("exclude", exclude);
        }
        return spec
                .query((rs, n) -> new OutboxMessage(
                        rs.getObject("id", UUID.class),
                        rs.getString("aggregate_type"),
                        rs.getObject("aggregate_id", UUID.class),
                        rs.getString("event_type"),
                        rs.getString("payload"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getInt("attempts") + 1))
                .optional();
    }

    private void markPublished(UUID id) {
        jdbc.sql("UPDATE outbox_events SET published_at = now(), attempts = attempts + 1, last_error = NULL WHERE id = :id")
                .param("id", id)
                .update();
    }

    private void recordFailure(OutboxMessage m, RuntimeException e) {
        failed.increment();
        long backoffSeconds = props.retryBackoff().toSeconds() * m.attempt();
        String error = e.getClass().getSimpleName() + ": " + e.getMessage();
        if (error.length() > MAX_ERROR_LENGTH) {
            error = error.substring(0, MAX_ERROR_LENGTH);
        }
        log.warn("Outbox event {} ({}) delivery failed, attempt {}/{}", m.id(), m.eventType(), m.attempt(),
                props.maxAttempts(), e);
        String lastError = error;
        tx.executeWithoutResult(s -> jdbc.sql("""
                        UPDATE outbox_events
                           SET attempts = attempts + 1,
                               last_error = :error,
                               next_attempt_at = now() + make_interval(secs => :backoff)
                         WHERE id = :id AND published_at IS NULL
                        """)
                .param("error", lastError)
                .param("backoff", backoffSeconds)
                .param("id", m.id())
                .update());
    }

    private double pendingCount() {
        return countWhere("published_at IS NULL AND attempts < " + props.maxAttempts());
    }

    private double deadCount() {
        return countWhere("published_at IS NULL AND attempts >= " + props.maxAttempts());
    }

    private double countWhere(String condition) {
        try {
            return jdbc.sql("SELECT count(*) FROM outbox_events WHERE " + condition).query(Long.class).single();
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    private record Attempt(OutboxMessage message, RuntimeException error) {
    }
}
