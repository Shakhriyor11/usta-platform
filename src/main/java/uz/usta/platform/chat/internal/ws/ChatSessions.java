package uz.usta.platform.chat.internal.ws;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;
import uz.usta.platform.chat.internal.application.SessionCloser;

/** Shu instansiyadagi ochiq WebSocket sessiyalari va ularning egalari. */
@Component
class ChatSessions implements SessionCloser, WebSocketHandlerDecoratorFactory {

    private static final Logger log = LoggerFactory.getLogger(ChatSessions.class);

    private final Map<String, WebSocketSession> open = new ConcurrentHashMap<>();
    private final Map<String, UUID> owners = new ConcurrentHashMap<>();

    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                open.put(session.getId(), session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                open.remove(session.getId());
                owners.remove(session.getId());
                super.afterConnectionClosed(session, status);
            }
        };
    }

    /** STOMP CONNECT muvaffaqiyatli bo'lganda. STOMP sessiya ID si = WebSocket sessiya ID si. */
    void bind(String sessionId, UUID userId) {
        owners.put(sessionId, userId);
    }

    /** @return {@code null} — sessiya autentifikatsiyadan o'tmagan yoki yopilgan */
    UUID ownerOf(String sessionId) {
        return sessionId == null ? null : owners.get(sessionId);
    }

    @Override
    public void closeSessionsOf(UUID userId) {
        owners.forEach((sessionId, owner) -> {
            if (owner.equals(userId)) {
                owners.remove(sessionId);
                WebSocketSession session = open.get(sessionId);
                if (session != null) {
                    try {
                        session.close(CloseStatus.POLICY_VIOLATION);
                    } catch (IOException e) {
                        log.warn("Failed to close WebSocket session {}: {}", sessionId, e.getMessage());
                    }
                }
            }
        });
    }
}
