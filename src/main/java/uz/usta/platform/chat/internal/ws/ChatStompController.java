package uz.usta.platform.chat.internal.ws;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import uz.usta.platform.chat.internal.application.ChatService;
import uz.usta.platform.shared.error.BusinessException;

/** STOMP orqali xabar yuborish. Natija barcha ishtirokchilarga mavzu orqali keladi (REST bilan bir xil). */
@Controller
class ChatStompController {

    private final ChatService chat;

    ChatStompController(ChatService chat) {
        this.chat = chat;
    }

    record SendFrame(String content) {
    }

    @MessageMapping("/orders.{orderId}.chat")
    void send(@DestinationVariable UUID orderId, @Payload SendFrame frame, Principal principal) {
        chat.send(orderId, StompAuthInterceptor.user(principal).id(), frame.content());
    }

    @MessageMapping("/orders.{orderId}.chat.read")
    void read(@DestinationVariable UUID orderId, Principal principal) {
        chat.markRead(orderId, StompAuthInterceptor.user(principal).id());
    }

    /** Biznes xatolar faqat yuboruvchiga: {@code /user/queue/errors}. */
    @MessageExceptionHandler(BusinessException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    Map<String, Object> onError(BusinessException e) {
        return Map.of("code", e.getErrorCode().code(), "status", e.getErrorCode().status().value());
    }
}
