package uz.usta.platform.profile.internal.domain;

import org.springframework.http.HttpStatus;
import uz.usta.platform.shared.error.ErrorCode;

public enum ProfileErrorCode implements ErrorCode {
    PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "error.profile.not-found"),
    MASTER_PROFILE_REQUIRED(HttpStatus.CONFLICT, "error.profile.master-profile-required"),
    VERIFICATION_INVALID_STATE(HttpStatus.CONFLICT, "error.profile.verification-invalid-state"),
    VERIFICATION_MISSING_DOCUMENTS(HttpStatus.BAD_REQUEST, "error.profile.verification-missing-documents"),
    VERIFICATION_WORK_AREA_REQUIRED(HttpStatus.BAD_REQUEST, "error.profile.verification-work-area-required"),
    DOCUMENTS_LOCKED(HttpStatus.CONFLICT, "error.profile.documents-locked"),
    DOCUMENT_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "error.profile.document-limit-exceeded"),
    ADDRESS_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "error.profile.address-limit-exceeded");

    private final HttpStatus status;
    private final String messageKey;

    ProfileErrorCode(HttpStatus status, String messageKey) {
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
