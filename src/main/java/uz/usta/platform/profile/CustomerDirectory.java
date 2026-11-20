package uz.usta.platform.profile;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Mijoz profili va manzillari haqida boshqa modullar (order, review) uchun so'rovlar. */
public interface CustomerDirectory {

    /**
     * Mijozning saqlangan manzili.
     *
     * @throws uz.usta.platform.shared.error.BusinessException 404 — manzil topilmasa yoki boshqa foydalanuvchiniki bo'lsa
     */
    AddressSnapshot address(UUID userId, UUID addressId);

    /** Mijozlar ismlari (profil to'ldirilmagan bo'lsa — natijada bo'lmaydi). */
    Map<UUID, String> names(Collection<UUID> userIds);

    record AddressSnapshot(String title, String address, String details, double latitude, double longitude) {
    }
}
