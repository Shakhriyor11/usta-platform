package uz.usta.platform.chat.internal.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import uz.usta.platform.shared.security.CorsProperties;

/**
 * STOMP over WebSocket: {@code wss://<host>/ws}.
 * <ul>
 *   <li>CONNECT — {@code Authorization: Bearer <access token>} native sarlavhasi majburiy;</li>
 *   <li>SUBSCRIBE {@code /topic/orders.{orderId}.chat} — faqat buyurtma ishtirokchilari;</li>
 *   <li>SEND {@code /app/orders.{orderId}.chat} — {@code {"content":"..."}}; xatolar {@code /user/queue/errors} ga.</li>
 * </ul>
 * In-memory broker; instansiyalar orasidagi tarqatish — Redis pub/sub ({@code ChatBroadcaster}).
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocketMessageBroker
class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthInterceptor auth;
    private final ChatSessions sessions;
    private final ChatDeliveryInterceptor delivery;
    private final CorsProperties cors;

    WebSocketConfig(StompAuthInterceptor auth, ChatSessions sessions, ChatDeliveryInterceptor delivery,
                    CorsProperties cors) {
        this.auth = auth;
        this.sessions = sessions;
        this.delivery = delivery;
        this.cors = cors;
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(sessions);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(delivery);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Origin sarlavhasi bo'lmagan mobil ilovalar ham ulanadi; brauzerlar — faqat CORS ro'yxatidagi domenlardan
        registry.addEndpoint("/ws").setAllowedOriginPatterns(cors.allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue").setHeartbeatValue(new long[] {10_000, 10_000})
                .setTaskScheduler(heartbeatScheduler());
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(auth);
    }

    private static ThreadPoolTaskScheduler heartbeatScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }
}
