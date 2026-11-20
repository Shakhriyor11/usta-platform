package uz.usta.platform.profile.internal.application;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import uz.usta.platform.identity.IdentityEvents;
import uz.usta.platform.profile.ProfileEvents;
import uz.usta.platform.profile.internal.persistence.MasterProfileRepository;
import uz.usta.platform.shared.outbox.OutboxMessage;
import uz.usta.platform.shared.outbox.OutboxPublisher;

/**
 * Foydalanuvchi bloklansa — usta profili nofaol bo'ladi (qidiruvda ko'rinmaydi), blokdan chiqarilsa — faol.
 * Idempotent: event qayta kelsa natija o'zgarmaydi.
 */
@Component
class IdentityEventsListener {

    private static final Logger log = LoggerFactory.getLogger(IdentityEventsListener.class);

    private final MasterProfileRepository profiles;
    private final OutboxPublisher outbox;
    private final JsonMapper json;

    IdentityEventsListener(MasterProfileRepository profiles, OutboxPublisher outbox, JsonMapper json) {
        this.profiles = profiles;
        this.outbox = outbox;
        this.json = json;
    }

    @EventListener(condition = "#m.eventType == '" + IdentityEvents.USER_BLOCKED + "'")
    @Transactional
    public void onBlocked(OutboxMessage m) {
        setActive(json.readValue(m.payload(), IdentityEvents.UserBlocked.class).userId(), false);
    }

    @EventListener(condition = "#m.eventType == '" + IdentityEvents.USER_UNBLOCKED + "'")
    @Transactional
    public void onUnblocked(OutboxMessage m) {
        setActive(json.readValue(m.payload(), IdentityEvents.UserUnblocked.class).userId(), true);
    }

    private void setActive(UUID userId, boolean active) {
        profiles.findById(userId).ifPresent(p -> {
            if (p.isActive() != active) {
                p.setActive(active);
                outbox.publish(ProfileEvents.AGGREGATE, userId, ProfileEvents.MASTER_PROFILE_CHANGED,
                        new ProfileEvents.MasterProfileChanged(userId));
                log.info("Master {} active={} (user {} event)", userId, active, active ? "unblocked" : "blocked");
            }
        });
    }
}
