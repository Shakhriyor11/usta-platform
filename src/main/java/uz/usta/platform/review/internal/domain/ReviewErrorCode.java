package uz.usta.platform.review.internal.domain;

import org.springframework.http.HttpStatus;
import uz.usta.platform.shared.error.ErrorCode;

public enum ReviewErrorCode implements ErrorCode {
    ALREADY_REVIEWED(HttpStatus.CONFLICT, "error.review.already-reviewed");

    private final HttpStatus status;
    private final String messageKey;

    ReviewErrorCode(HttpStatus status, String messageKey) {
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
