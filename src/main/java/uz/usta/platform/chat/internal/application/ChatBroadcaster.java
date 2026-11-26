package uz.usta.platform.chat.internal.application;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * Chat hodisalarini WebSocket obunachilariga yetkazadi. API bir nechta instansiyada ishlaganda mijoz va usta
 * turli instansiyalarga ulangan bo'lishi mumkin, shuning uchun hodisa avval Redis pub/sub kanaliga
 * ({@code ws:chat}) chiqariladi, har instansiya uni o'zining STOMP broker'iga uzatadi.
 * Faqat tranzaksiya commit bo'lgach yuboriladi — saqlanmagan xabar efirga chiqmaydi.
 */
@Component
public class ChatBroadcaster {

    static final String CHANNEL = "ws:chat";
    static final String DISCONNECT_CHANNEL = "ws:disconnect";
    private static final Logger log = LoggerFactory.getLogger(ChatBroadcaster.class);

    private final StringRedisTemplate redis;
    private final JsonMapper json;

    ChatBroadcaster(StringRedisTemplate redis, JsonMapper json) {
        this.redis = redis;
        this.json = json;
    }

    /** STOMP mavzusi: mijoz va usta {@code /topic/orders.{orderId}.chat} ga obuna bo'ladi. */
    public static String topic(UUID orderId) {
        return "/topic/orders." + orderId + ".chat";
    }

    /** @param event {@code type} maydoni bor JSON-obyekt (MESSAGE, READ) */
    record Envelope(UUID orderId, ChatEvent event) {
    }

    public void publishAfterCommit(UUID orderId, ChatEvent event) {
        Runnable send = () -> {
            try {
                redis.convertAndSend(CHANNEL, json.writeValueAsString(new Envelope(orderId, event)));
            } catch (RuntimeException e) {
                log.warn("Chat broadcast failed for order {}: {}", orderId, e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    /** Foydalanuvchining sessiyalarini barcha instansiyalarda uzadi. Redis xatosi chaqiruvchiga uzatiladi. */
    void disconnect(UUID userId) {
        redis.convertAndSend(DISCONNECT_CHANNEL, userId.toString());
    }

    @Configuration(proxyBeanMethods = false)
    static class RedisFanout {

        @Bean
        RedisMessageListenerContainer chatFanoutContainer(RedisConnectionFactory factory, JsonMapper json,
                                                          SimpMessagingTemplate stomp, SessionCloser sessions) {
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(factory);
            container.addMessageListener((message, pattern) -> {
                try {
                    Envelope e = json.readValue(new String(message.getBody(), StandardCharsets.UTF_8), Envelope.class);
                    stomp.convertAndSend(topic(e.orderId()), e.event());
                } catch (RuntimeException ex) {
                    log.warn("Chat fanout failed: {}", ex.getMessage());
                }
            }, new ChannelTopic(CHANNEL));
            container.addMessageListener((message, pattern) -> sessions.closeSessionsOf(
                    UUID.fromString(new String(message.getBody(), StandardCharsets.UTF_8))),
                    new ChannelTopic(DISCONNECT_CHANNEL));
            return container;
        }
    }
}
