package uz.usta.platform.shared.error;

import org.springframework.http.HttpStatus;

/** Barcha modullar uchun umumiy xatolik kodlari. */
public enum CommonErrorCode implements ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "error.validation-failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "error.malformed-request"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "error.bad-request"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "error.unauthorized"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "error.access-denied"),
    /** Foydalanuvchi bloklangan (access token hali amal qilsa ham). */
    USER_BLOCKED(HttpStatus.FORBIDDEN, "error.identity.user-blocked"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "error.not-found"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "error.resource-not-found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "error.method-not-allowed"),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "error.not-acceptable"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "error.unsupported-media-type"),
    PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "error.payload-too-large"),
    CONFLICT(HttpStatus.CONFLICT, "error.conflict"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "error.concurrent-modification"),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "error.too-many-requests"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "error.service-unavailable"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "error.internal");

    private final HttpStatus status;
    private final String messageKey;

    CommonErrorCode(HttpStatus status, String messageKey) {
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
