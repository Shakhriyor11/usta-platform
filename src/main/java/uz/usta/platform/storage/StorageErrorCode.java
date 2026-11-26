package uz.usta.platform.storage;

import org.springframework.http.HttpStatus;
import uz.usta.platform.shared.error.ErrorCode;

public enum StorageErrorCode implements ErrorCode {
    FILE_EMPTY(HttpStatus.BAD_REQUEST, "error.storage.file-empty"),
    FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "error.storage.file-too-large"),
    FILE_TYPE_NOT_ALLOWED(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "error.storage.file-type-not-allowed"),
    IMAGE_INVALID(HttpStatus.BAD_REQUEST, "error.storage.image-invalid"),
    IMAGE_DIMENSIONS(HttpStatus.BAD_REQUEST, "error.storage.image-dimensions"),
    IMAGE_NOT_SQUARE(HttpStatus.BAD_REQUEST, "error.storage.image-not-square"),
    STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "error.storage.unavailable");

    private final HttpStatus status;
    private final String messageKey;

    StorageErrorCode(HttpStatus status, String messageKey) {
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
