package uz.usta.platform.chat.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.usta.platform.chat.internal.application.ChatEvent.MessageView;
import uz.usta.platform.chat.internal.application.ChatService;
import uz.usta.platform.shared.security.AuthenticatedUser;
import uz.usta.platform.shared.web.ApiPaths;

@Tag(name = "Chat", description = """
        Buyurtma chati (mijoz <-> usta). Real vaqt: STOMP over WebSocket wss://<host>/ws —
        CONNECT sarlavhasi Authorization: Bearer <token>; SUBSCRIBE /topic/orders.{orderId}.chat;
        SEND /app/orders.{orderId}.chat {"content":"..."}; o'qildi — SEND /app/orders.{orderId}.chat.read.""")
@RestController
class ChatController {

    private final ChatService chat;

    ChatController(ChatService chat) {
        this.chat = chat;
    }

    record SendRequest(@NotBlank @Size(max = 2000) String content) {
    }

    @Operation(summary = "Chat holati: yozish mumkinmi, o'qilmaganlar, ikkinchi tomon qachongacha o'qigan")
    @GetMapping(ApiPaths.V1 + "/orders/{orderId}/chat")
    ChatService.ChatInfo info(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orderId) {
        return chat.info(orderId, user.id());
    }

    @Operation(summary = "Xabarlar (yangilaridan eskilariga)", description = "Keyingi sahifa: ?before=<eng eski xabar id>")
    @GetMapping(ApiPaths.V1 + "/orders/{orderId}/chat/messages")
    List<MessageView> messages(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orderId,
                               @RequestParam(required = false) UUID before,
                               @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return chat.history(orderId, user.id(), before, limit, false);
    }

    @Operation(summary = "Xabar yuborish (WebSocket o'rniga REST)")
    @PostMapping(ApiPaths.V1 + "/orders/{orderId}/chat/messages")
    @ResponseStatus(HttpStatus.CREATED)
    MessageView send(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orderId,
                     @Valid @RequestBody SendRequest body) {
        return chat.send(orderId, user.id(), body.content());
    }

    @Operation(summary = "O'qildi deb belgilash")
    @PostMapping(ApiPaths.V1 + "/orders/{orderId}/chat/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void read(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orderId) {
        chat.markRead(orderId, user.id());
    }

    @Operation(summary = "Admin: buyurtma chati (nizolarni ko'rib chiqish uchun)", tags = "Admin: Orders")
    @GetMapping(ApiPaths.ADMIN + "/orders/{orderId}/chat/messages")
    List<MessageView> adminMessages(@PathVariable UUID orderId, @RequestParam(required = false) UUID before,
                                    @RequestParam(defaultValue = "100") @Min(1) @Max(100) int limit) {
        return chat.history(orderId, null, before, limit, true);
    }
}
