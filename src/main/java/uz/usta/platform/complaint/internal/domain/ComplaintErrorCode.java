package uz.usta.platform.complaint.internal.domain;

import org.springframework.http.HttpStatus;
import uz.usta.platform.shared.error.ErrorCode;

public enum ComplaintErrorCode implements ErrorCode {
    ALREADY_OPEN(HttpStatus.CONFLICT, "error.complaint.already-open"),
    NOT_ALLOWED(HttpStatus.CONFLICT, "error.complaint.not-allowed"),
    INVALID_STATE(HttpStatus.CONFLICT, "error.complaint.invalid-state"),
    ORDER_RESOLUTION_REQUIRED(HttpStatus.BAD_REQUEST, "error.complaint.order-resolution-required");

    private final HttpStatus status;
    private final String messageKey;

    ComplaintErrorCode(HttpStatus status, String messageKey) {
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
