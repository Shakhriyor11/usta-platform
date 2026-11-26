package uz.usta.platform.catalog.internal.application;

import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import uz.usta.platform.catalog.CatalogEvents;
import uz.usta.platform.catalog.internal.persistence.MasterServicePriceRepository;
import uz.usta.platform.catalog.internal.persistence.PortfolioItemRepository;
import uz.usta.platform.identity.IdentityEvents;
import uz.usta.platform.shared.outbox.OutboxMessage;
import uz.usta.platform.shared.outbox.OutboxPublisher;
import uz.usta.platform.storage.FileStorage;

/** O'chirilgan ustaning xizmat narxlari va portfolio rasmlari. Idempotent. */
@Component
class DeletedMasterCatalogEraser {

    private final MasterServicePriceRepository prices;
    private final PortfolioItemRepository portfolio;
    private final FileStorage storage;
    private final OutboxPublisher outbox;
    private final JsonMapper json;

    DeletedMasterCatalogEraser(MasterServicePriceRepository prices, PortfolioItemRepository portfolio,
                               FileStorage storage, OutboxPublisher outbox, JsonMapper json) {
        this.prices = prices;
        this.portfolio = portfolio;
        this.storage = storage;
        this.outbox = outbox;
        this.json = json;
    }

    @EventListener(condition = "#m.eventType == '" + IdentityEvents.USER_DELETED + "'")
    @Transactional
    public void onDeleted(OutboxMessage m) {
        UUID userId = json.readValue(m.payload(), IdentityEvents.UserDeleted.class).userId();
        portfolio.findByMasterIdOrderByCreatedAtDesc(userId).forEach(item -> {
            storage.deleteAfterCommit(item.getFileKey());
            portfolio.delete(item);
        });
        var offered = prices.findByMasterIdOrderByCreatedAtAsc(userId);
        if (!offered.isEmpty()) {
            prices.deleteAll(offered);
            outbox.publish("master_profile", userId, CatalogEvents.MASTER_SERVICES_CHANGED,
                    new CatalogEvents.MasterServicesChanged(userId));
        }
    }
}
