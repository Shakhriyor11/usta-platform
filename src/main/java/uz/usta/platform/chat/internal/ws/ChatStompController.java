package uz.usta.platform.chat.internal.ws;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import uz.usta.platform.chat.internal.application.ChatService;
import uz.usta.platform.shared.error.BusinessException;
import uz.usta.platform.shared.error.CommonErrorCode;
import uz.usta.platform.shared.error.ErrorCode;

/** STOMP orqali xabar yuborish. Natija barcha ishtirokchilarga mavzu orqali keladi (REST bilan bir xil). */
@Controller
class ChatStompController {

    private static final Logger log = LoggerFactory.getLogger(ChatStompController.class);

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
        return error(e.getErrorCode());
    }

    /** O'qib bo'lmaydigan kadr (noto'g'ri JSON). */
    @MessageExceptionHandler(MessageConversionException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    Map<String, Object> onMalformed(MessageConversionException e) {
        return error(CommonErrorCode.MALFORMED_REQUEST);
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    Map<String, Object> onUnexpected(Exception e) {
        log.error("Unhandled STOMP exception", e);
        return error(CommonErrorCode.INTERNAL_ERROR);
    }

    private static Map<String, Object> error(ErrorCode code) {
        return Map.of("code", code.code(), "status", code.status().value());
    }
}
