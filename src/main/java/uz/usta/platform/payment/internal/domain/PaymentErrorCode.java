package uz.usta.platform.payment.internal.domain;

import org.springframework.http.HttpStatus;
import uz.usta.platform.shared.error.ErrorCode;

public enum PaymentErrorCode implements ErrorCode {
    NOTHING_TO_SETTLE(HttpStatus.CONFLICT, "error.payment.nothing-to-settle"),
    AMOUNT_MISMATCH(HttpStatus.CONFLICT, "error.payment.amount-mismatch"),
    INVALID_PERIOD(HttpStatus.BAD_REQUEST, "error.payment.invalid-period"),
    USER_HAS_DEBT(HttpStatus.CONFLICT, "error.payment.user-has-debt");

    private final HttpStatus status;
    private final String messageKey;

    PaymentErrorCode(HttpStatus status, String messageKey) {
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
