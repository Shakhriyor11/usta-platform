package uz.usta.platform.chat.internal.ws;

import java.util.Optional;
import java.util.UUID;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;
import uz.usta.platform.chat.internal.application.ChatService;

/**
 * Chiqayotgan chat hodisalari: obuna paytidagi ruxsat yetarli emas — usta buyurtmadan voz kechib, boshqasi
 * qabul qilishi mumkin. Har yetkazishda obunachi hozir ham shu buyurtma ishtirokchisi ekani tekshiriladi.
 */
@Component
class ChatDeliveryInterceptor implements ChannelInterceptor {

    private final ChatSessions sessions;
    private final ChatService chat;

    ChatDeliveryInterceptor(ChatSessions sessions, ChatService chat) {
        this.sessions = sessions;
        this.chat = chat;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) != SimpMessageType.MESSAGE) {
            return message;
        }
        Optional<UUID> orderId = ChatDestinations.orderId(
                SimpMessageHeaderAccessor.getDestination(message.getHeaders()));
        if (orderId.isEmpty()) {
            return message;
        }
        UUID userId = sessions.ownerOf(SimpMessageHeaderAccessor.getSessionId(message.getHeaders()));
        return userId != null && chat.canRead(orderId.get(), userId) ? message : null;
    }
}
