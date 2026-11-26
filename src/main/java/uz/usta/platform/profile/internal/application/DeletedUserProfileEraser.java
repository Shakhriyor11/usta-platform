package uz.usta.platform.profile.internal.application;

import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import uz.usta.platform.identity.IdentityEvents;
import uz.usta.platform.profile.ProfileEvents;
import uz.usta.platform.profile.internal.persistence.AddressRepository;
import uz.usta.platform.profile.internal.persistence.CustomerProfileRepository;
import uz.usta.platform.profile.internal.persistence.MasterProfileRepository;
import uz.usta.platform.profile.internal.persistence.VerificationDocumentRepository;
import uz.usta.platform.shared.outbox.OutboxMessage;
import uz.usta.platform.shared.outbox.OutboxPublisher;
import uz.usta.platform.storage.FileStorage;

/** O'chirilgan hisobning profillari, manzillari, hujjatlari va rasmlari. Idempotent. */
@Component
class DeletedUserProfileEraser {

    private final CustomerProfileRepository customers;
    private final AddressRepository addresses;
    private final MasterProfileRepository masters;
    private final VerificationDocumentRepository documents;
    private final FileStorage storage;
    private final OutboxPublisher outbox;
    private final JsonMapper json;

    DeletedUserProfileEraser(CustomerProfileRepository customers, AddressRepository addresses,
                             MasterProfileRepository masters, VerificationDocumentRepository documents,
                             FileStorage storage, OutboxPublisher outbox, JsonMapper json) {
        this.customers = customers;
        this.addresses = addresses;
        this.masters = masters;
        this.documents = documents;
        this.storage = storage;
        this.outbox = outbox;
        this.json = json;
    }

    @EventListener(condition = "#m.eventType == '" + IdentityEvents.USER_DELETED + "'")
    @Transactional
    public void onDeleted(OutboxMessage m) {
        UUID userId = json.readValue(m.payload(), IdentityEvents.UserDeleted.class).userId();
        customers.findById(userId).ifPresent(profile -> {
            storage.deleteAfterCommit(profile.getAvatarKey());
            customers.delete(profile);
        });
        addresses.deleteAll(addresses.findByUserIdOrderByCreatedAtAsc(userId));
        documents.findByMasterIdOrderByCreatedAtAsc(userId).forEach(doc -> {
            storage.deleteAfterCommit(doc.getFileKey());
            documents.delete(doc);
        });
        masters.findById(userId).ifPresent(profile -> {
            storage.deleteAfterCommit(profile.getAvatarKey());
            profile.anonymize();
            outbox.publish(ProfileEvents.AGGREGATE, userId, ProfileEvents.MASTER_PROFILE_CHANGED,
                    new ProfileEvents.MasterProfileChanged(userId));
        });
    }
}
