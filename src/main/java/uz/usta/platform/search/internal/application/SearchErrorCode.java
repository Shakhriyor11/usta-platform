package uz.usta.platform.search.internal.application;

import org.springframework.http.HttpStatus;
import uz.usta.platform.shared.error.ErrorCode;

public enum SearchErrorCode implements ErrorCode {
    TARGET_REQUIRED(HttpStatus.BAD_REQUEST, "error.search.target-required"),
    LOCATION_REQUIRED(HttpStatus.BAD_REQUEST, "error.search.location-required"),
    INVALID_PRICE_RANGE(HttpStatus.BAD_REQUEST, "error.search.invalid-price-range");

    private final HttpStatus status;
    private final String messageKey;

    SearchErrorCode(HttpStatus status, String messageKey) {
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
