package uz.usta.platform.search;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Buyurtmani ustalarga taqsimlash uchun nomzodlar (order moduli ishlatadi). Faqat mijozlarga ko'rinadigan,
 * xizmatni faol taklif qiladigan va nuqta o'z ish hududi ichida bo'lgan ustalar qaytadi.
 */
public interface MasterMatcher {

    /**
     * Avval online ustalar (masofa bo'yicha, teng bo'lsa — reyting), {@code limit} ga yetmasa — offline ustalar
     * ham shu tartibda.
     */
    List<Candidate> nearest(UUID serviceId, double latitude, double longitude, int limit,
                            Collection<UUID> excludeMasterIds);

    /** Aniq usta shu xizmat va nuqtaga mos keladimi (mijoz ustani o'zi tanlaganda). */
    Optional<Candidate> match(UUID masterId, UUID serviceId, double latitude, double longitude);

    record Candidate(UUID masterId, int distanceMeters, BigDecimal priceFrom) {
    }
}
