package uz.usta.platform.order.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uz.usta.platform.order.internal.domain.OrderStatus.ACCEPTED;
import static uz.usta.platform.order.internal.domain.OrderStatus.CANCELLED;
import static uz.usta.platform.order.internal.domain.OrderStatus.COMMISSION;
import static uz.usta.platform.order.internal.domain.OrderStatus.COMPLETED;
import static uz.usta.platform.order.internal.domain.OrderStatus.CREATED;
import static uz.usta.platform.order.internal.domain.OrderStatus.DISPUTED;
import static uz.usta.platform.order.internal.domain.OrderStatus.EXPIRED;
import static uz.usta.platform.order.internal.domain.OrderStatus.IN_PROGRESS;
import static uz.usta.platform.order.internal.domain.OrderStatus.REVIEWED;
import static uz.usta.platform.order.internal.domain.OrderStatus.SEARCHING;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import uz.usta.platform.shared.error.BusinessException;

class OrderStatusTest {

    /** Hujjatdagi (docs/architecture.md) status diagrammasi bilan bir xil bo'lishi shart. */
    static final Map<OrderStatus, Set<OrderStatus>> EXPECTED = Map.of(
            CREATED, EnumSet.of(SEARCHING, CANCELLED),
            SEARCHING, EnumSet.of(ACCEPTED, EXPIRED, CANCELLED),
            ACCEPTED, EnumSet.of(IN_PROGRESS, CANCELLED, SEARCHING),
            IN_PROGRESS, EnumSet.of(COMPLETED, DISPUTED),
            COMPLETED, EnumSet.of(COMMISSION, DISPUTED),
            COMMISSION, EnumSet.of(REVIEWED, DISPUTED),
            DISPUTED, EnumSet.of(COMPLETED, CANCELLED),
            REVIEWED, EnumSet.noneOf(OrderStatus.class),
            CANCELLED, EnumSet.noneOf(OrderStatus.class),
            EXPIRED, EnumSet.noneOf(OrderStatus.class));

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void transitionTableMatchesSpecification(OrderStatus from) {
        for (OrderStatus to : OrderStatus.values()) {
            assertThat(from.canTransitionTo(to))
                    .as("%s -> %s", from, to)
                    .isEqualTo(EXPECTED.get(from).contains(to));
        }
    }

    @Test
    void terminalStates() {
        assertThat(EnumSet.allOf(OrderStatus.class).stream().filter(OrderStatus::isTerminal))
                .containsExactlyInAnyOrder(REVIEWED, CANCELLED, EXPIRED);
    }

    @Test
    void entityRejectsIllegalTransitionAndKeepsStatus() {
        Order order = newOrder();
        assertThatThrownBy(() -> order.moveTo(COMPLETED, Instant.now()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(OrderErrorCode.INVALID_STATUS_TRANSITION);
        assertThat(order.getStatus()).isEqualTo(CREATED);
    }

    @Test
    void entityRecordsLifecycleTimestamps() {
        Order order = newOrder();
        Instant t = Instant.parse("2026-11-18T10:00:00Z");
        assertThat(order.moveTo(SEARCHING, t)).isEqualTo(CREATED);
        order.assignMaster(UUID.randomUUID(), new BigDecimal("100000"));
        order.moveTo(ACCEPTED, t.plusSeconds(60));
        order.moveTo(IN_PROGRESS, t.plusSeconds(120));
        order.setFinalPrice(new BigDecimal("150000.5"));
        order.moveTo(COMPLETED, t.plusSeconds(180));
        order.moveTo(COMMISSION, t.plusSeconds(181));
        order.moveTo(REVIEWED, t.plusSeconds(240));

        assertThat(order.getAcceptedAt()).isEqualTo(t.plusSeconds(60));
        assertThat(order.getStartedAt()).isEqualTo(t.plusSeconds(120));
        assertThat(order.getCompletedAt()).isEqualTo(t.plusSeconds(180));
        assertThat(order.getClosedAt()).isEqualTo(t.plusSeconds(240));
        assertThat(order.getPrice()).isEqualByComparingTo("150000.50");
    }

    private static Order newOrder() {
        return new Order(UUID.randomUUID(), UUID.randomUUID(), null, "test", null, "addr", null, 41.3, 69.2, null,
                Instant.now().plusSeconds(900));
    }
}
