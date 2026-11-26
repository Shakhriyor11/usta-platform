package uz.usta.platform.catalog.internal.domain;

import org.springframework.http.HttpStatus;
import uz.usta.platform.shared.error.ErrorCode;

public enum CatalogErrorCode implements ErrorCode {
    SLUG_TAKEN(HttpStatus.CONFLICT, "error.catalog.slug-taken"),
    CATEGORY_CYCLE(HttpStatus.CONFLICT, "error.catalog.category-cycle"),
    CATEGORY_DEPTH_EXCEEDED(HttpStatus.CONFLICT, "error.catalog.category-depth-exceeded"),
    SERVICE_NOT_AVAILABLE(HttpStatus.CONFLICT, "error.catalog.service-not-available"),
    MASTER_SERVICE_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "error.catalog.master-service-limit-exceeded"),
    PORTFOLIO_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "error.catalog.portfolio-limit-exceeded"),
    CATEGORY_NOT_EMPTY(HttpStatus.CONFLICT, "error.catalog.category-not-empty"),
    UNKNOWN_ICON(HttpStatus.BAD_REQUEST, "error.catalog.unknown-icon");

    private final HttpStatus status;
    private final String messageKey;

    CatalogErrorCode(HttpStatus status, String messageKey) {
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
