package uz.usta.platform.order.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import uz.usta.platform.order.internal.application.AdminOrderService.Resolution;
import uz.usta.platform.order.internal.domain.OfferStatus;
import uz.usta.platform.order.internal.domain.OrderStatus;
import uz.usta.platform.shared.validation.PlainText;
import uz.usta.platform.shared.web.GeoPoint;

final class OrderDtos {

    private OrderDtos() {
    }

    // ---------------------------------------------------------------- so'rovlar

    @Schema(description = "Manzil: addressId (saqlangan) yoki address (shu buyurtma uchun) — bittasi majburiy")
    record CreateOrderRequest(
            @NotNull UUID serviceId,
            @Schema(example = "Oshxonadagi jo'mrak oqyapti, almashtirish kerak") @NotBlank @Size(max = 2000) @PlainText String description,
            UUID addressId,
            @Valid InlineAddress address,
            @Schema(description = "Rejalashtirilgan vaqt (ISO-8601). Bo'sh — imkon qadar tez") Instant scheduledAt,
            @Schema(description = "Qidiruvdan tanlangan usta — birinchi taklif faqat unga yuboriladi") UUID preferredMasterId) {
    }

    record InlineAddress(
            @Schema(example = "Toshkent, Chilonzor 9-kvartal, 12-uy") @NotBlank @Size(max = 500) @PlainText String address,
            @Size(max = 255) @PlainText String details,
            @NotNull @Valid GeoPoint location) {
    }

    record CancelRequest(@Size(max = 500) @PlainText String reason) {
    }

    record ReasonRequest(@NotBlank @Size(max = 500) @PlainText String reason) {
    }

    record CompleteRequest(
            @Schema(example = "180000", description = "Yakuniy narx, UZS (musbat)") @NotNull @DecimalMin("1")
            @DecimalMax("999999999999.99") @Digits(integer = 12, fraction = 2) BigDecimal price) {
    }

    record ResolveDisputeRequest(
            @NotNull Resolution resolution,
            @Schema(description = "COMPLETED uchun: narx hali kiritilmagan bo'lsa majburiy") @DecimalMin("1")
            @DecimalMax("999999999999.99") @Digits(integer = 12, fraction = 2) BigDecimal price,
            @NotBlank @Size(max = 500) @PlainText String note) {
    }

    // ---------------------------------------------------------------- javoblar

    record AddressResponse(String title, String address, String details, GeoPoint location) {
    }

    @Schema(description = "phone — faqat usta qabul qilgan va ish faol (ACCEPTED/IN_PROGRESS) bo'lganda")
    record MasterBrief(UUID id, String fullName, String avatarUrl, BigDecimal ratingAvg, int ratingCount,
                       String phone) {
    }

    @Schema(description = "phone — faqat ish faol (ACCEPTED/IN_PROGRESS) bo'lganda")
    record CustomerBrief(UUID id, String fullName, String phone) {
    }

    record AttachmentResponse(UUID id, String url, String mediaType, long sizeBytes, Instant createdAt) {
    }

    record HistoryResponse(OrderStatus from, OrderStatus to, UUID changedBy, String reason, Instant changedAt) {
    }

    record OfferResponse(UUID masterId, String masterName, OfferStatus status, int distanceMeters, Instant createdAt,
                         Instant expiresAt, Instant respondedAt) {
    }

    record OrderSummary(UUID id, OrderStatus status, UUID serviceId, String serviceName, String address,
                        Instant scheduledAt, BigDecimal priceFrom, BigDecimal price, UUID masterId, String masterName,
                        Instant createdAt) {
    }

    @Schema(description = "Mijoz uchun buyurtma. estimatedPriceMin/Max — faqat qidiruv paytida (SEARCHING): taklif "
            + "yuborilgan ustalarning \"narx dan\" oralig'i (usta topilmaguncha bo'sh bo'lishi mumkin). priceFrom — "
            + "qabul qilgan ustaning \"narx dan\" qiymati, price — yakuniy narx")
    record OrderResponse(UUID id, OrderStatus status, UUID serviceId, String serviceName, String description,
                         AddressResponse address, Instant scheduledAt, Instant searchDeadline,
                         BigDecimal estimatedPriceMin, BigDecimal estimatedPriceMax, BigDecimal priceFrom,
                         BigDecimal price, MasterBrief master, List<AttachmentResponse> attachments,
                         List<HistoryResponse> history, String cancelReason, Instant createdAt, Instant acceptedAt,
                         Instant startedAt, Instant completedAt, Instant closedAt) {
    }

    @Schema(description = "Ustaga kelgan taklif. Aniq manzil (podyezd, xonadon) va mijoz telefoni qabul qilingandan keyin ochiladi")
    record MasterOfferResponse(UUID orderId, UUID serviceId, String serviceName, String description, String address,
                               BigDecimal distanceKm, Instant scheduledAt, Instant expiresAt,
                               List<AttachmentResponse> attachments, Instant createdAt) {
    }

    record MasterOrderResponse(UUID id, OrderStatus status, UUID serviceId, String serviceName, String description,
                               AddressResponse address, BigDecimal distanceKm, Instant scheduledAt,
                               BigDecimal priceFrom, BigDecimal price, BigDecimal commissionAmount,
                               CustomerBrief customer, List<AttachmentResponse> attachments, OfferStatus offerStatus,
                               Instant offerExpiresAt, String cancelReason, Instant createdAt, Instant acceptedAt,
                               Instant startedAt, Instant completedAt) {
    }

    record AdminOrderResponse(OrderResponse order, CustomerBrief customer, BigDecimal commissionAmount,
                              List<OfferResponse> offers) {
    }
}
