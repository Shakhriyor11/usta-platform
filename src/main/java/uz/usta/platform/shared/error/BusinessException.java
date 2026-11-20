package uz.usta.platform.shared.error;

import java.util.Map;

/**
 * Biznes qoidasi buzilganda tashlanadigan yagona exception. HTTP status, kod va lokalizatsiya
 * qilingan matn {@link ErrorCode} dan olinadi; {@code args} — xabardagi {0}, {1} parametrlari.
 */
public class BusinessException extends RuntimeException {

    private final transient ErrorCode errorCode;
    private final transient Object[] args;
    private final transient Map<String, Object> details;

    public BusinessException(ErrorCode errorCode, Object... args) {
        this(errorCode, Map.of(), args);
    }

    public BusinessException(ErrorCode errorCode, Map<String, Object> details, Object... args) {
        super(errorCode.code());
        this.errorCode = errorCode;
        this.args = args == null ? new Object[0] : args.clone();
        this.details = Map.copyOf(details);
    }

    public static BusinessException notFound(String resource, Object id) {
        return new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND, resource, id);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Object[] getArgs() {
        return args.clone();
    }

    /** Javobga qo'shimcha maydon sifatida qo'shiladigan ma'lumotlar (masalan {@code retryAfterSeconds}). */
    public Map<String, Object> getDetails() {
        return details;
    }
}
