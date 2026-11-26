package uz.usta.platform.chat.internal.ws;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** STOMP manzillari. */
final class ChatDestinations {

    private static final Pattern CHAT_TOPIC = Pattern.compile(
            "^/topic/orders\\.([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\\.chat$");

    private ChatDestinations() {
    }

    /** Buyurtma chati mavzusi bo'lsa — buyurtma ID si. */
    static Optional<UUID> orderId(String destination) {
        if (destination == null) {
            return Optional.empty();
        }
        Matcher m = CHAT_TOPIC.matcher(destination);
        return m.matches() ? Optional.of(UUID.fromString(m.group(1))) : Optional.empty();
    }
}
