package uz.usta.platform.chat.internal.ws;

import java.security.Principal;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import uz.usta.platform.chat.internal.application.ChatService;
import uz.usta.platform.shared.security.AuthenticatedUser;
import uz.usta.platform.shared.security.UserRevocations;

/**
 * STOMP xavfsizligi. HTTP handshake ochiq (brauzer WebSocket'ga sarlavha qo'sha olmaydi), autentifikatsiya
 * CONNECT kadrida: REST bilan bir xil access token (JWT) tekshiriladi. Qolgan kadrlar oq ro'yxat bo'yicha:
 * obuna — faqat ruxsat etilgan mavzularga, yuborish — faqat {@code /app/**} ga (mavzuga to'g'ridan-to'g'ri
 * yozib, boshqa foydalanuvchi nomidan soxta hodisa tarqatib bo'lmaydi).
 */
@Component
class StompAuthInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompAuthInterceptor.class);
    private static final String APP_PREFIX = "/app/";

    private final JwtDecoder jwtDecoder;
    private final Converter<Jwt, AbstractAuthenticationToken> converter;
    private final ChatService chat;
    private final ChatSessions sessions;
    private final UserRevocations revocations;

    StompAuthInterceptor(JwtDecoder jwtDecoder, Converter<Jwt, AbstractAuthenticationToken> converter,
                         ChatService chat, ChatSessions sessions, UserRevocations revocations) {
        this.jwtDecoder = jwtDecoder;
        this.converter = converter;
        this.chat = chat;
        this.sessions = sessions;
        this.revocations = revocations;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message; // heartbeat va uzilish hodisalari
        }
        switch (accessor.getCommand()) {
            case CONNECT, STOMP -> {
                Authentication auth = authenticate(accessor.getFirstNativeHeader("Authorization"));
                accessor.setUser(auth);
                sessions.bind(accessor.getSessionId(), user(auth).id());
            }
            case SUBSCRIBE -> authorizeSubscription(activeUser(accessor), accessor.getDestination());
            case SEND -> authorizeSend(activeUser(accessor), accessor.getDestination());
            case UNSUBSCRIBE, DISCONNECT, ACK, NACK, BEGIN, COMMIT, ABORT -> {
                // ruxsat talab qilmaydi
            }
            default -> throw new MessageDeliveryException("ACCESS_DENIED"); // server kadrlari (MESSAGE va h.k.)
        }
        return message;
    }

    private AuthenticatedUser activeUser(StompHeaderAccessor accessor) {
        AuthenticatedUser user = user(accessor.getUser());
        if (revocations.isRevoked(user.id())) {
            throw new MessageDeliveryException("USER_BLOCKED");
        }
        return user;
    }

    private Authentication authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessageDeliveryException("UNAUTHORIZED: missing Bearer token");
        }
        Authentication auth;
        try {
            auth = converter.convert(jwtDecoder.decode(header.substring(7).strip()));
        } catch (JwtException e) {
            throw new MessageDeliveryException("UNAUTHORIZED: invalid token");
        }
        if (revocations.isRevoked(user(auth).id())) {
            throw new MessageDeliveryException("USER_BLOCKED");
        }
        return auth;
    }

    private void authorizeSend(AuthenticatedUser user, String destination) {
        if (destination == null || !destination.startsWith(APP_PREFIX)) {
            log.warn("STOMP send denied: user={} destination={}", user.id(), destination);
            throw new MessageDeliveryException("ACCESS_DENIED");
        }
    }

    private void authorizeSubscription(AuthenticatedUser user, String destination) {
        if (destination != null && destination.startsWith("/user/queue/")) {
            return; // shaxsiy navbat (xatolar)
        }
        Optional<UUID> orderId = ChatDestinations.orderId(destination);
        if (orderId.isEmpty() || !chat.canRead(orderId.get(), user.id())) {
            log.info("STOMP subscription denied: user={} destination={}", user.id(), destination);
            throw new MessageDeliveryException("ACCESS_DENIED");
        }
    }

    static AuthenticatedUser user(Principal principal) {
        if (principal instanceof Authentication a && a.getPrincipal() instanceof AuthenticatedUser u) {
            return u;
        }
        throw new MessageDeliveryException("UNAUTHORIZED");
    }
}
