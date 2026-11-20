package uz.usta.platform.shared.outbox;

/**
 * Outbox xabarini manzilga yetkazish strategiyasi. Standart — Spring {@code ApplicationEvent}
 * (in-process). Keyinchalik RabbitMQ/Kafka adapteri shu interfeysni amalga oshiradi.
 * Exception tashlansa — xabar keyinroq qayta yuboriladi.
 */
public interface OutboxDispatcher {

    void dispatch(OutboxMessage message);
}
