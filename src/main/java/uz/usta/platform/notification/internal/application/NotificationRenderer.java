package uz.usta.platform.notification.internal.application;

import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/** Bildirishnoma matnini kerakli tilda hosil qiladi (in-app — so'rov tilida, push — qurilma tilida). */
@Component
public class NotificationRenderer {

    public static final Locale DEFAULT = Locale.forLanguageTag("uz");

    private final MessageSource messages;

    NotificationRenderer(MessageSource messages) {
        this.messages = messages;
    }

    public record Text(String title, String body) {
    }

    public Text render(NotificationType type, List<String> args, Locale locale) {
        Object[] a = args.toArray();
        return new Text(messages.getMessage(type.titleKey(), a, type.name(), locale),
                messages.getMessage(type.bodyKey(), a, "", locale));
    }
}
