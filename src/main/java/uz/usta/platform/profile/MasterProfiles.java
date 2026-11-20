package uz.usta.platform.profile;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Boshqa modullar (catalog, search, order, review) uchun usta profili haqida so'rovlar va amallar. */
public interface MasterProfiles {

    /** Usta profili mavjudligini tekshiradi, aks holda {@code MASTER_PROFILE_REQUIRED} (409). */
    void requireProfile(UUID masterId);

    /** Usta mijozlarga ko'rinadimi: tasdiqlangan ({@code APPROVED}) va faol. */
    boolean isPublic(UUID masterId);

    /**
     * Qidiruv indeksi uchun: usta ko'rinadigan va ish hududi kiritilgan bo'lsa — snapshot, aks holda bo'sh.
     */
    Optional<MasterSnapshot> searchable(UUID masterId);

    /** To'liq qayta indekslash uchun: barcha ko'rinadigan ustalar ID'lari. */
    List<UUID> searchableIds();

    /** Ro'yxatlar uchun qisqa ma'lumot (ism, avatar, reyting). Topilmaganlar natijada bo'lmaydi. */
    Map<UUID, MasterSummary> summaries(Collection<UUID> masterIds);

    /**
     * Yangi bahoni reyting agregatiga qo'shadi (atomik SQL, chaqiruvchining tranzaksiyasida) va
     * {@link ProfileEvents#MASTER_PROFILE_CHANGED} eventini chiqaradi.
     */
    void applyRating(UUID masterId, int rating);

    record MasterSnapshot(UUID masterId, String fullName, String avatarKey, int experienceYears, double latitude,
                          double longitude, int workRadiusKm, BigDecimal ratingAvg, int ratingCount) {
    }

    record MasterSummary(UUID masterId, String fullName, String avatarKey, BigDecimal ratingAvg, int ratingCount) {
    }
}
