package uz.usta.platform.catalog;

import java.util.UUID;

/** Catalog modulining outbox eventlari (qidiruv keshini invalidatsiya qilish uchun). */
public final class CatalogEvents {

    public static final String MASTER_SERVICES_CHANGED = "catalog.master-services-changed";
    public static final String CATALOG_CHANGED = "catalog.changed";

    private CatalogEvents() {
    }

    public record MasterServicesChanged(UUID masterId) {
    }

    public record CatalogChanged(String entity, UUID id) {
    }
}
