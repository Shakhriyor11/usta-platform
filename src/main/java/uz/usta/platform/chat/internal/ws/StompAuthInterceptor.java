package uz.usta.platform.chat.internal.ws;

import java.security.Principal;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
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
 * CONNECT kadrida: REST bilan bir xil access token (JWT) tekshiriladi. Obuna faqat ruxsat etilgan mavzularga.
 */
@Component
class StompAuthInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompAuthInterceptor.class);
    private static final Pattern CHAT_TOPIC = Pattern.compile("^/topic/orders\\.([0-9a-fA-F-]{36})\\.chat$");

    private final JwtDecoder jwtDecoder;
    private final Converter<Jwt, AbstractAuthenticationToken> converter;
    private final ChatService chat;
    private final UserRevocations revocations;

    StompAuthInterceptor(JwtDecoder jwtDecoder, Converter<Jwt, AbstractAuthenticationToken> converter,
                         ChatService chat, UserRevocations revocations) {
        this.jwtDecoder = jwtDecoder;
        this.converter = converter;
        this.chat = chat;
        this.revocations = revocations;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
            return message;
        }
        if (command == StompCommand.SUBSCRIBE || command == StompCommand.SEND) {
            AuthenticatedUser user = user(accessor.getUser());
            if (command == StompCommand.SUBSCRIBE) {
                authorizeSubscription(user, accessor.getDestination());
            }
        }
        return message;
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

    private void authorizeSubscription(AuthenticatedUser user, String destination) {
        if (destination == null) {
            throw new MessageDeliveryException("ACCESS_DENIED");
        }
        if (destination.startsWith("/user/queue/")) {
            return; // shaxsiy navbat (xatolar)
        }
        Matcher m = CHAT_TOPIC.matcher(destination);
        if (!m.matches() || !chat.canRead(UUID.fromString(m.group(1)), user.id())) {
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
