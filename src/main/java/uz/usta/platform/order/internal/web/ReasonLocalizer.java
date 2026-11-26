package uz.usta.platform.order.internal.web;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import uz.usta.platform.order.OrderApi;
import uz.usta.platform.order.internal.application.SystemReasons;
import uz.usta.platform.shared.i18n.Messages;

/**
 * Bazadagi sabab kodlarini so'rov tilidagi matnga o'giradi; foydalanuvchi yozgan sabab o'zicha qoladi.
 * Tizim kodlari faqat muallifsiz yozuvlarda o'giriladi — foydalanuvchi "tizim xabari" yasay olmaydi.
 */
@Component
class ReasonLocalizer {

    /** Eski yozuvlarda prefiksdan keyin bo'sh joy bor. */
    private static final Pattern COMPLAINT =
            Pattern.compile(Pattern.quote(OrderApi.COMPLAINT_REASON_PREFIX) + " ?([A-Z_]+)");

    private final Messages messages;

    ReasonLocalizer(Messages messages) {
        this.messages = messages;
    }

    String localize(String reason, UUID actorId) {
        if (reason == null) {
            return null;
        }
        if (actorId == null && reason.startsWith(SystemReasons.PREFIX)) {
            return messages.get("reason." + reason.substring(SystemReasons.PREFIX.length()));
        }
        var complaint = COMPLAINT.matcher(reason);
        return complaint.matches()
                ? messages.get("reason.complaint",
                        messages.get("complaint.reason." + complaint.group(1).toLowerCase(Locale.ROOT)))
                : reason;
    }
}
