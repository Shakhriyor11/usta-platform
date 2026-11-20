package uz.usta.platform.shared.outbox;

import java.util.UUID;

/**
 * Domen eventini biznes o'zgarish bilan BIR tranzaksiyada {@code outbox_events} jadvaliga yozadi.
 * Keyin {@code OutboxRelay} uni iste'molchilarga (hozircha in-process, keyinchalik broker) yetkazadi.
 *
 * <p>Faqat faol tranzaksiya ichida chaqirilishi shart — aks holda {@code IllegalTransactionStateException}.
 */
public interface OutboxPublisher {

    /**
     * @param aggregateType masalan {@code "order"}
     * @param aggregateId   agregat ID si
     * @param eventType     masalan {@code "order.status-changed"}
     * @param payload       JSON ga seriyalanadigan obyekt (record/DTO)
     * @return yaratilgan event ID si
     */
    UUID publish(String aggregateType, UUID aggregateId, String eventType, Object payload);
}
