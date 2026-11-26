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
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
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
import uz.usta.platform.shared.validation.PlainText;
import uz.usta.platform.shared.web.ApiPaths;

@Tag(name = "Chat", description = """
        Buyurtma chati (mijoz ↔ usta), har usta bilan alohida. Usta buyurtmani qabul qilgach ochiladi, buyurtma
        yopilgach faqat o'qiladi. Rasm — `POST .../chat/images` (REST), qolganini REST yoki WebSocket orqali.

        **Real vaqt: STOMP over WebSocket `wss://<host>/ws`** (heartbeat 10 s)

        | Kadr | Manzil | Izoh |
        |---|---|---|
        | `CONNECT` | — | sarlavha `Authorization: Bearer <accessToken>` majburiy |
        | `SUBSCRIBE` | `/topic/orders.{orderId}.chat` | faqat shu buyurtma ishtirokchisi; hodisa — `ChatEvent` sxemasi |
        | `SUBSCRIBE` | `/user/queue/errors` | o'zingiz yuborgan xabar xatolari: `{"code":"CHAT_CLOSED","status":409}` |
        | `SEND` | `/app/orders.{orderId}.chat` | `{"content":"..."}`, 2000 belgigacha |
        | `SEND` | `/app/orders.{orderId}.chat.read` | o'qildi deb belgilash |

        Boshqa manzilga `SEND`/`SUBSCRIBE` qilinsa yoki token yaroqsiz bo'lsa, server `ERROR` kadri yuboradi
        (`message` sarlavhasida `ACCESS_DENIED`, `UNAUTHORIZED…` yoki `USER_BLOCKED`) va ulanishni yopadi — yangi
        token bilan qayta ulaning. Bloklangan yoki o'chirilgan foydalanuvchining ulanishi uziladi. Uzilganda
        qayta ulanib, oxirgi xabarlarni REST orqali oling.""")
@RestController
class ChatController {

    private final ChatService chat;

    ChatController(ChatService chat) {
        this.chat = chat;
    }

    record SendRequest(@NotBlank @Size(max = 2000) @PlainText String content) {
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

    @Operation(summary = "Rasm yuborish", description = "JPEG/PNG/WEBP, 10 MB gacha; izoh ixtiyoriy. Rasm metama'lumotlari "
            + "(joylashuv va h.k.) o'chiriladi")
    @PostMapping(path = ApiPaths.V1 + "/orders/{orderId}/chat/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    MessageView sendImage(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orderId,
                          @RequestPart("file") MultipartFile file,
                          @RequestParam(required = false) @Size(max = 2000) @PlainText String caption) {
        return chat.sendImage(orderId, user.id(), file, caption);
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
