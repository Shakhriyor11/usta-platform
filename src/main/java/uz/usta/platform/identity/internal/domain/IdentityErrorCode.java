package uz.usta.platform.identity.internal.domain;

import org.springframework.http.HttpStatus;
import uz.usta.platform.shared.error.ErrorCode;

public enum IdentityErrorCode implements ErrorCode {
    INVALID_PHONE_NUMBER(HttpStatus.BAD_REQUEST, "error.identity.invalid-phone"),
    OTP_RESEND_TOO_EARLY(HttpStatus.TOO_MANY_REQUESTS, "error.identity.otp-resend-too-early"),
    OTP_EXPIRED(HttpStatus.BAD_REQUEST, "error.identity.otp-expired"),
    OTP_INVALID(HttpStatus.BAD_REQUEST, "error.identity.otp-invalid"),
    OTP_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "error.identity.otp-attempts-exceeded"),
    SMS_SEND_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "error.identity.sms-send-failed"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "error.identity.invalid-refresh-token"),
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "error.identity.refresh-token-reused"),
    USER_BLOCKED(HttpStatus.FORBIDDEN, "error.identity.user-blocked"),
    ROLE_NOT_SELECTABLE(HttpStatus.BAD_REQUEST, "error.identity.role-not-selectable"),
    CANNOT_BLOCK_ADMIN(HttpStatus.CONFLICT, "error.identity.cannot-block-admin");

    private final HttpStatus status;
    private final String messageKey;

    IdentityErrorCode(HttpStatus status, String messageKey) {
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
