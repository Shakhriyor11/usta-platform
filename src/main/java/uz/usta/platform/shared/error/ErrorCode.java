package uz.usta.platform.shared.error;

import org.springframework.http.HttpStatus;

/**
 * Mashina o'qiy oladigan xatolik kodi. Har bir modul o'z enum'ini shu interfeys orqali e'lon qiladi
 * (masalan {@code OrderErrorCode implements ErrorCode}). Matn {@link #messageKey()} orqali i18n'dan olinadi.
 */
public interface ErrorCode {

    /** Barqaror, API mijozlari tayanadigan kod, masalan {@code ORDER_INVALID_TRANSITION}. */
    String code();

    HttpStatus status();

    /** {@code i18n/messages*.properties} dagi kalit. */
    String messageKey();
}
