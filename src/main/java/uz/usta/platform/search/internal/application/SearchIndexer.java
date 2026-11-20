package uz.usta.platform.search.internal.application;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import uz.usta.platform.catalog.CatalogApi;
import uz.usta.platform.catalog.CatalogApi.Offering;
import uz.usta.platform.catalog.CatalogEvents;
import uz.usta.platform.profile.MasterProfiles;
import uz.usta.platform.profile.MasterProfiles.MasterSnapshot;
import uz.usta.platform.profile.ProfileEvents;
import uz.usta.platform.search.internal.persistence.SearchIndexRepository;
import uz.usta.platform.shared.outbox.OutboxMessage;

/**
 * Qidiruv read-modelini profile va catalog eventlari asosida yangilaydi. Har doim manbadan joriy holatni
 * o'qiydi (event tarkibiga tayanmaydi) — shuning uchun idempotent va eventlar tartibiga bog'liq emas.
 */
@Service
public class SearchIndexer {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexer.class);

    private final SearchIndexRepository index;
    private final MasterProfiles profiles;
    private final CatalogApi catalog;
    private final SearchCache cache;
    private final JsonMapper json;

    SearchIndexer(SearchIndexRepository index, MasterProfiles profiles, CatalogApi catalog, SearchCache cache,
                  JsonMapper json) {
        this.index = index;
        this.profiles = profiles;
        this.catalog = catalog;
        this.cache = cache;
        this.json = json;
    }

    @EventListener(condition = "#m.aggregateType == '" + ProfileEvents.AGGREGATE + "'")
    @Transactional
    public void onMasterChanged(OutboxMessage m) {
        // profile.* va catalog.master-services-changed — barchasida masterId bor
        JsonNode masterId = json.readTree(m.payload()).get("masterId");
        if (masterId != null) {
            reindex(UUID.fromString(masterId.asString()));
        }
    }

    @EventListener(condition = "#m.eventType == '" + CatalogEvents.CATALOG_CHANGED + "'")
    @Transactional
    public void onCatalogChanged(OutboxMessage m) {
        CatalogEvents.CatalogChanged e = json.readValue(m.payload(), CatalogEvents.CatalogChanged.class);
        if ("service".equals(e.entity())) {
            catalog.mastersOffering(e.id()).forEach(this::reindex);
        }
    }

    /** Bitta ustani indeksga qo'shadi/yangilaydi yoki (ko'rinmasa) olib tashlaydi. */
    @Transactional
    public void reindex(UUID masterId) {
        Optional<MasterSnapshot> snapshot = profiles.searchable(masterId);
        List<Offering> offerings = snapshot.isPresent() ? catalog.activeOfferings(masterId) : List.of();
        if (snapshot.isPresent() && !offerings.isEmpty()) {
            index.upsert(snapshot.get(), offerings);
            log.debug("Search index: master {} upserted with {} services", masterId, offerings.size());
        } else if (index.delete(masterId)) {
            log.debug("Search index: master {} removed", masterId);
        }
        cache.invalidateAfterCommit();
    }

    /** Indeks bo'sh bo'lsa (yangi o'rnatish yoki V7 migratsiyasidan keyin) — ishga tushganda quriladi. */
    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {
        try {
            if (index.indexedIds().isEmpty() && !profiles.searchableIds().isEmpty()) {
                rebuild();
            }
        } catch (RuntimeException e) {
            log.error("Search index bootstrap failed — run POST /api/v1/admin/search/reindex", e);
        }
    }

    /** To'liq qayta qurish (admin yoki migratsiyadan keyin). */
    @Transactional
    public int rebuild() {
        Set<UUID> ids = new HashSet<>(profiles.searchableIds());
        ids.addAll(index.indexedIds());
        ids.forEach(this::reindex);
        log.info("Search index rebuilt: {} masters processed", ids.size());
        return ids.size();
    }
}
