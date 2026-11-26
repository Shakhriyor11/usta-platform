package uz.usta.platform.catalog;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import uz.usta.platform.shared.i18n.LocalizedText;

/** Katalog haqida boshqa modullar (search, order) uchun so'rovlar. */
public interface CatalogApi {

    /**
     * Faol xizmat (kategoriyasi va uning ota-bobolari ham faol).
     *
     * @throws uz.usta.platform.shared.error.BusinessException {@code SERVICE_NOT_AVAILABLE} — topilmasa yoki nofaol
     */
    ServiceInfo requireActiveService(UUID serviceId);

    /** Kategoriya va uning barcha faol avlodlaridagi faol xizmatlar. Kategoriya nofaol bo'lsa — 404. */
    List<UUID> activeServiceIdsInCategory(UUID categoryId);

    /** Xizmat nomlari (nofaollari ham — tarixiy buyurtmalar uchun). */
    Map<UUID, LocalizedText> serviceNames(Collection<UUID> serviceIds);

    /** Ustaning mijozlarga ko'rinadigan takliflari: faol narx + faol xizmat. */
    List<Offering> activeOfferings(UUID masterId);

    /** Ustaning shu xizmat uchun faol "narx dan" qiymati. */
    Optional<BigDecimal> priceFrom(UUID masterId, UUID serviceId);

    /** Xizmatni ko'rsatadigan barcha ustalar (indeksni yangilash uchun). */
    List<UUID> mastersOffering(UUID serviceId);

    /** Kategoriya yoki uning avlodlaridagi xizmatlarni ko'rsatadigan ustalar (nofaollari ham — indeks uchun). */
    List<UUID> mastersOfferingInCategory(UUID categoryId);

    record ServiceInfo(UUID id, UUID categoryId, LocalizedText name) {
    }

    record Offering(UUID serviceId, BigDecimal priceFrom) {
    }
}
