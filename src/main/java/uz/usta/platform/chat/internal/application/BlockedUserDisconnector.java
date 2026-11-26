package uz.usta.platform.chat.internal.application;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import uz.usta.platform.identity.IdentityEvents;
import uz.usta.platform.shared.outbox.OutboxMessage;

/** Bloklangan yoki o'chirilgan foydalanuvchining ochiq WebSocket sessiyalari barcha instansiyalarda uziladi. */
@Component
class BlockedUserDisconnector {

    private final ChatBroadcaster broadcaster;
    private final JsonMapper json;

    BlockedUserDisconnector(ChatBroadcaster broadcaster, JsonMapper json) {
        this.broadcaster = broadcaster;
        this.json = json;
    }

    @EventListener(condition = "#m.eventType == '" + IdentityEvents.USER_BLOCKED + "'")
    public void onBlocked(OutboxMessage m) {
        broadcaster.disconnect(json.readValue(m.payload(), IdentityEvents.UserBlocked.class).userId());
    }

    @EventListener(condition = "#m.eventType == '" + IdentityEvents.USER_DELETED + "'")
    public void onDeleted(OutboxMessage m) {
        broadcaster.disconnect(json.readValue(m.payload(), IdentityEvents.UserDeleted.class).userId());
    }
}
