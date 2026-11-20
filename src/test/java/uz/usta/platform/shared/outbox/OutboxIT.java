package uz.usta.platform.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import uz.usta.platform.support.IntegrationTest;

@IntegrationTest
@Import(OutboxIT.Listeners.class)
class OutboxIT {

    @Autowired
    OutboxPublisher publisher;

    @Autowired
    OutboxRelay relay;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    Recorder recorder;

    record OrderCreated(UUID orderId, String service) {
    }

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM outbox_events").update();
        recorder.received.clear();
        recorder.failNext = 0;
    }

    @Test
    void publishRequiresTransaction() {
        assertThatThrownBy(() -> publisher.publish("order", UUID.randomUUID(), "test.order-created", "x"))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void eventIsPersistedWithBusinessTransactionAndDelivered() {
        UUID orderId = UUID.randomUUID();
        UUID eventId = tx.execute(s -> publisher.publish("order", orderId, "test.order-created",
                new OrderCreated(orderId, "Santexnik")));

        assertThat(relay.relayPending()).isEqualTo(1);

        assertThat(recorder.received).singleElement().satisfies(m -> {
            assertThat(m.id()).isEqualTo(eventId);
            assertThat(m.aggregateId()).isEqualTo(orderId);
            assertThat(m.payload()).contains("\"service\"").contains("Santexnik");
        });
        assertThat(publishedAt(eventId)).isTrue();
        assertThat(relay.relayPending()).isZero(); // ikkinchi marta yuborilmaydi
    }

    @Test
    void rolledBackTransactionLeavesNoEvent() {
        tx.executeWithoutResult(s -> {
            publisher.publish("order", UUID.randomUUID(), "test.order-created", "x");
            s.setRollbackOnly();
        });
        assertThat(jdbc.sql("SELECT count(*) FROM outbox_events").query(Long.class).single()).isZero();
    }

    @Test
    void failedDeliveryIsRetried() {
        recorder.failNext = 1;
        UUID eventId = tx.execute(s -> publisher.publish("order", UUID.randomUUID(), "test.order-created", "x"));

        assertThat(relay.relayPending()).isZero();
        assertThat(jdbc.sql("SELECT attempts FROM outbox_events WHERE id = :id").param("id", eventId)
                .query(Integer.class).single()).isEqualTo(1);
        assertThat(publishedAt(eventId)).isFalse();

        assertThat(relay.relayPending()).isEqualTo(1); // backoff testda 0
        assertThat(publishedAt(eventId)).isTrue();
    }

    private boolean publishedAt(UUID id) {
        return jdbc.sql("SELECT published_at IS NOT NULL FROM outbox_events WHERE id = :id")
                .param("id", id).query(Boolean.class).single();
    }

    static class Recorder {
        final List<OutboxMessage> received = new CopyOnWriteArrayList<>();
        volatile int failNext;

        @EventListener(condition = "#m.eventType == 'test.order-created'")
        void on(OutboxMessage m) {
            if (failNext > 0) {
                failNext--;
                throw new IllegalStateException("consumer temporarily down");
            }
            received.add(m);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Listeners {
        @Bean
        Recorder recorder() {
            return new Recorder();
        }
    }
}
