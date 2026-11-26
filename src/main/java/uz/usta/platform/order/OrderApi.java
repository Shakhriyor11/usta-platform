package uz.usta.platform.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Boshqa modullar (review, chat, complaint, admin) uchun buyurtma haqida so'rovlar va amallar. */
public interface OrderApi {

    /**
     * Shikoyat orqali ochilgan nizo sababi: {@code complaint:<ComplaintReason nomi>}. Bazada kod saqlanadi, matn
     * o'qish paytida foydalanuvchi tilida chiqadi.
     */
    String COMPLAINT_REASON_PREFIX = "complaint:";

    /**
     * Buyurtmani qulflaydi va mijoz uni baholay olishini tekshiradi (egasi va holati COMPLETED/COMMISSION).
     * Chaqiruvchining tranzaksiyasida bajariladi.
     *
     * @throws uz.usta.platform.shared.error.BusinessException 404 — egasi bo'lmasa, 409 — holat mos emas
     */
    ReviewTarget lockForReview(UUID orderId, UUID customerId);

    /** {@link #lockForReview} dan keyin, xuddi shu tranzaksiyada: COMMISSION -> REVIEWED. */
    void markReviewed(UUID orderId, UUID customerId);

    /** Buyurtma ishtirokchilari va holati. @throws BusinessException 404 */
    Participants participants(UUID orderId);

    /**
     * Nizo ochish (mijoz yoki biriktirilgan usta): IN_PROGRESS/COMPLETED/COMMISSION -> DISPUTED.
     * Chaqiruvchining tranzaksiyasida.
     */
    void openDispute(UUID orderId, UUID actorId, String reason);

    /** Admin qarori: DISPUTED -> COMPLETED (komissiya qayta hisoblanadi) yoki CANCELLED (komissiya qaytariladi). */
    void resolveDispute(UUID adminId, UUID orderId, DisputeResolution resolution, BigDecimal price, String note);

    /** Solishtirish uchun: shu oraliqda yakunlangan buyurtmalar va ularda qayd etilgan komissiya. */
    List<CommissionedOrder> completedBetween(Instant from, Instant to);

    enum DisputeResolution { COMPLETED, CANCELLED }

    record ReviewTarget(UUID orderId, UUID customerId, UUID masterId) {
    }

    /** @param status buyurtma holati nomi ({@code OrderStatus}) */
    record Participants(UUID orderId, UUID customerId, UUID masterId, String status) {

        public boolean isParticipant(UUID userId) {
            return userId.equals(customerId) || userId.equals(masterId);
        }
    }

    /** @param commissionAmount buyurtmada qayd etilgan komissiya; bekor qilingan bo'lsa — null */
    record CommissionedOrder(UUID orderId, UUID masterId, String status, BigDecimal price,
                             BigDecimal commissionAmount, Instant completedAt) {
    }
}
