package uz.usta.platform.order.internal.domain;

import org.springframework.http.HttpStatus;
import uz.usta.platform.shared.error.ErrorCode;

public enum OrderErrorCode implements ErrorCode {
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "error.order.invalid-status-transition"),
    ORDER_ALREADY_TAKEN(HttpStatus.CONFLICT, "error.order.already-taken"),
    OFFER_NOT_AVAILABLE(HttpStatus.CONFLICT, "error.order.offer-not-available"),
    MASTER_NOT_AVAILABLE(HttpStatus.CONFLICT, "error.order.master-not-available"),
    MASTER_NOT_ACTIVE(HttpStatus.FORBIDDEN, "error.order.master-not-active"),
    CANNOT_ORDER_FROM_SELF(HttpStatus.BAD_REQUEST, "error.order.cannot-order-from-self"),
    ADDRESS_REQUIRED(HttpStatus.BAD_REQUEST, "error.order.address-required"),
    INVALID_SCHEDULE(HttpStatus.BAD_REQUEST, "error.order.invalid-schedule"),
    ORDER_NOT_EDITABLE(HttpStatus.CONFLICT, "error.order.not-editable"),
    ATTACHMENT_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "error.order.attachment-limit-exceeded"),
    ORDER_NOT_REVIEWABLE(HttpStatus.CONFLICT, "error.order.not-reviewable"),
    PRICE_REQUIRED(HttpStatus.BAD_REQUEST, "error.order.price-required"),
    PRICE_NOT_POSITIVE(HttpStatus.BAD_REQUEST, "error.order.price-not-positive"),
    MASTER_HAS_OVERDUE_DEBT(HttpStatus.FORBIDDEN, "error.order.master-overdue-debt"),
    USER_HAS_ACTIVE_ORDERS(HttpStatus.CONFLICT, "error.order.user-has-active-orders");

    private final HttpStatus status;
    private final String messageKey;

    OrderErrorCode(HttpStatus status, String messageKey) {
        this.status = status;
        this.messageKey = messageKey;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String messageKey() {
        return messageKey;
    }
}
