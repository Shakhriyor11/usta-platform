package uz.usta.platform.chat.internal.domain;

import org.springframework.http.HttpStatus;
import uz.usta.platform.shared.error.ErrorCode;

public enum ChatErrorCode implements ErrorCode {
    CHAT_NOT_AVAILABLE(HttpStatus.CONFLICT, "error.chat.not-available"),
    CHAT_CLOSED(HttpStatus.CONFLICT, "error.chat.closed");

    private final HttpStatus status;
    private final String messageKey;

    ChatErrorCode(HttpStatus status, String messageKey) {
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
