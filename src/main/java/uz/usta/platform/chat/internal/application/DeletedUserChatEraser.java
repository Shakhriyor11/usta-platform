package uz.usta.platform.chat.internal.application;

import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import uz.usta.platform.chat.internal.persistence.ChatMessageRepository;
import uz.usta.platform.identity.IdentityEvents;
import uz.usta.platform.shared.outbox.OutboxMessage;
import uz.usta.platform.storage.FileStorage;

/** O'chirilgan hisob chatda yuborgan rasmlar o'chiriladi (matnli yozishma ikkinchi tomon uchun qoladi). */
@Component
class DeletedUserChatEraser {

    private final ChatMessageRepository messages;
    private final FileStorage storage;
    private final JsonMapper json;

    DeletedUserChatEraser(ChatMessageRepository messages, FileStorage storage, JsonMapper json) {
        this.messages = messages;
        this.storage = storage;
        this.json = json;
    }

    @EventListener(condition = "#m.eventType == '" + IdentityEvents.USER_DELETED + "'")
    @Transactional
    public void onDeleted(OutboxMessage m) {
        UUID userId = json.readValue(m.payload(), IdentityEvents.UserDeleted.class).userId();
        messages.findImageKeys(userId).forEach(storage::deleteAfterCommit);
        messages.deleteImageOnly(userId);
        messages.removeImages(userId);
    }
}
