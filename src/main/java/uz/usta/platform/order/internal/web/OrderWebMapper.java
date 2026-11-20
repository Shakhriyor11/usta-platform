package uz.usta.platform.order.internal.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import uz.usta.platform.catalog.CatalogApi;
import uz.usta.platform.identity.UserDirectory;
import uz.usta.platform.order.internal.application.MasterOrderService.MasterView;
import uz.usta.platform.order.internal.application.MasterOrderService.OpenOffer;
import uz.usta.platform.order.internal.application.OrderQueryService;
import uz.usta.platform.order.internal.domain.Order;
import uz.usta.platform.order.internal.domain.OrderAttachment;
import uz.usta.platform.order.internal.domain.OrderOffer;
import uz.usta.platform.order.internal.web.OrderDtos.AddressResponse;
import uz.usta.platform.order.internal.web.OrderDtos.AdminOrderResponse;
import uz.usta.platform.order.internal.web.OrderDtos.AttachmentResponse;
import uz.usta.platform.order.internal.web.OrderDtos.CustomerBrief;
import uz.usta.platform.order.internal.web.OrderDtos.HistoryResponse;
import uz.usta.platform.order.internal.web.OrderDtos.MasterBrief;
import uz.usta.platform.order.internal.web.OrderDtos.MasterOfferResponse;
import uz.usta.platform.order.internal.web.OrderDtos.MasterOrderResponse;
import uz.usta.platform.order.internal.web.OrderDtos.OfferResponse;
import uz.usta.platform.order.internal.web.OrderDtos.OrderResponse;
import uz.usta.platform.order.internal.web.OrderDtos.OrderSummary;
import uz.usta.platform.profile.CustomerDirectory;
import uz.usta.platform.profile.MasterProfiles;
import uz.usta.platform.profile.MasterProfiles.MasterSummary;
import uz.usta.platform.shared.i18n.LocalizedText;
import uz.usta.platform.shared.web.GeoPoint;
import uz.usta.platform.storage.FileStorage;

/**
 * Entity -> DTO. Boshqa modullardan (xizmat nomi, usta/mijoz ismi, telefon) faqat ularning ochiq API'lari orqali
 * va ro'yxatlar uchun bitta so'rov bilan (N+1 yo'q) olinadi.
 */
@Component
class OrderWebMapper {

    private final CatalogApi catalog;
    private final MasterProfiles masters;
    private final CustomerDirectory customers;
    private final UserDirectory users;
    private final FileStorage storage;
    private final OrderQueryService query;

    OrderWebMapper(CatalogApi catalog, MasterProfiles masters, CustomerDirectory customers, UserDirectory users,
                   FileStorage storage, OrderQueryService query) {
        this.catalog = catalog;
        this.masters = masters;
        this.customers = customers;
        this.users = users;
        this.storage = storage;
        this.query = query;
    }

    // ---------------------------------------------------------------- mijoz

    List<OrderSummary> summaries(List<Order> orders) {
        Map<UUID, LocalizedText> names = catalog.serviceNames(ids(orders, Order::getServiceId));
        Map<UUID, MasterSummary> ms = masters.summaries(ids(orders, Order::getMasterId));
        return orders.stream().map(o -> new OrderSummary(o.getId(), o.getStatus(), o.getServiceId(),
                name(names, o.getServiceId()), o.getAddressText(), o.getScheduledAt(), o.getPriceFrom(), o.getPrice(),
                o.getMasterId(), o.getMasterId() == null || !ms.containsKey(o.getMasterId()) ? null
                        : ms.get(o.getMasterId()).fullName(), o.getCreatedAt())).toList();
    }

    OrderResponse toResponse(Order o) {
        MasterBrief master = null;
        if (o.getMasterId() != null) {
            MasterSummary m = masters.summaries(List.of(o.getMasterId())).get(o.getMasterId());
            String phone = o.getStatus().isActiveAssignment() ? users.phones(List.of(o.getMasterId())).get(o.getMasterId()) : null;
            if (m != null) {
                master = new MasterBrief(m.masterId(), m.fullName(), url(m.avatarKey()), m.ratingAvg(), m.ratingCount(),
                        phone);
            }
        }
        return new OrderResponse(o.getId(), o.getStatus(), o.getServiceId(), serviceName(o), o.getDescription(),
                fullAddress(o), o.getScheduledAt(), o.getSearchDeadline(), o.getPriceFrom(), o.getPrice(), master,
                attachments(o.getId()),
                query.history(o.getId()).stream().map(h -> new HistoryResponse(h.getOldStatus(), h.getNewStatus(),
                        h.getChangedBy(), h.getReason(), h.getChangedAt())).toList(),
                o.getCancelReason(), o.getCreatedAt(), o.getAcceptedAt(), o.getStartedAt(), o.getCompletedAt(),
                o.getClosedAt());
    }

    AttachmentResponse toResponse(OrderAttachment a) {
        return new AttachmentResponse(a.getId(), url(a.getFileKey()), a.getMediaType(), a.getSizeBytes(),
                a.getCreatedAt());
    }

    // ---------------------------------------------------------------- usta

    List<MasterOfferResponse> offers(List<OpenOffer> offers) {
        Map<UUID, LocalizedText> names = catalog.serviceNames(ids(offers.stream().map(OpenOffer::order).toList(),
                Order::getServiceId));
        return offers.stream().map(x -> new MasterOfferResponse(x.order().getId(), x.order().getServiceId(),
                name(names, x.order().getServiceId()), x.order().getDescription(), x.order().getAddressText(),
                km(x.offer().getDistanceMeters()), x.order().getScheduledAt(), x.offer().getExpiresAt(),
                attachments(x.order().getId()), x.offer().getCreatedAt())).toList();
    }

    MasterOrderResponse toMasterResponse(MasterView v) {
        Order o = v.order();
        boolean assigned = v.offer() == null;
        CustomerBrief customer = null;
        if (assigned) {
            String name = customers.names(List.of(o.getCustomerId())).get(o.getCustomerId());
            String phone = o.getStatus().isActiveAssignment()
                    ? users.phones(List.of(o.getCustomerId())).get(o.getCustomerId()) : null;
            customer = new CustomerBrief(o.getCustomerId(), name, phone);
        }
        AddressResponse address = assigned ? fullAddress(o) : new AddressResponse(null, o.getAddressText(), null, null);
        OrderOffer offer = v.offer();
        return new MasterOrderResponse(o.getId(), o.getStatus(), o.getServiceId(), serviceName(o), o.getDescription(),
                address, offer == null ? null : km(offer.getDistanceMeters()), o.getScheduledAt(), o.getPriceFrom(),
                o.getPrice(), assigned ? o.getCommissionAmount() : null, customer, attachments(o.getId()),
                offer == null ? null : offer.getStatus(), offer == null ? null : offer.getExpiresAt(),
                o.getCancelReason(), o.getCreatedAt(), o.getAcceptedAt(), o.getStartedAt(), o.getCompletedAt());
    }

    // ---------------------------------------------------------------- admin

    AdminOrderResponse toAdmin(Order o) {
        List<OrderOffer> offers = query.offers(o.getId());
        Map<UUID, MasterSummary> ms = masters.summaries(ids(offers, OrderOffer::getMasterId));
        return new AdminOrderResponse(toResponse(o),
                new CustomerBrief(o.getCustomerId(), customers.names(List.of(o.getCustomerId())).get(o.getCustomerId()),
                        users.phones(List.of(o.getCustomerId())).get(o.getCustomerId())),
                o.getCommissionAmount(),
                offers.stream().map(f -> new OfferResponse(f.getMasterId(),
                        ms.containsKey(f.getMasterId()) ? ms.get(f.getMasterId()).fullName() : null, f.getStatus(),
                        f.getDistanceMeters(), f.getCreatedAt(), f.getExpiresAt(), f.getRespondedAt())).toList());
    }

    // ---------------------------------------------------------------- yordamchilar

    private List<AttachmentResponse> attachments(UUID orderId) {
        return query.attachments(orderId).stream().map(this::toResponse).toList();
    }

    private String serviceName(Order o) {
        return name(catalog.serviceNames(List.of(o.getServiceId())), o.getServiceId());
    }

    private static AddressResponse fullAddress(Order o) {
        return new AddressResponse(o.getAddressTitle(), o.getAddressText(), o.getAddressDetails(),
                new GeoPoint(o.getLatitude(), o.getLongitude()));
    }

    private String url(String key) {
        return key == null ? null : storage.url(key);
    }

    private static String name(Map<UUID, LocalizedText> names, UUID id) {
        LocalizedText t = names.get(id);
        return t == null ? null : t.current();
    }

    private static BigDecimal km(int meters) {
        return BigDecimal.valueOf(meters / 1000d).setScale(1, RoundingMode.HALF_UP);
    }

    private static <T> Set<UUID> ids(Collection<T> items, Function<T, UUID> f) {
        Set<UUID> ids = new HashSet<>();
        items.stream().map(f).filter(Objects::nonNull).forEach(ids::add);
        return ids;
    }
}
