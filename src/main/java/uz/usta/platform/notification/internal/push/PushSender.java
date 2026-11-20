package uz.usta.platform.notification.internal.push;

import java.util.Map;

/**
 * Push adapteri. Provayder {@code app.push.provider} bilan tanlanadi: {@code log} (standart — faqat logga yozadi).
 * Firebase kalitlari berilganda FCM adapteri shu interfeys bilan qo'shiladi — biznes kod o'zgarmaydi.
 */
public interface PushSender {

    /**
     * @throws InvalidTokenException token eskirgan/o'chirilgan — chaqiruvchi uni bazadan o'chiradi
     */
    void send(String token, String title, String body, Map<String, String> data);

    class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) {
            super(message);
        }
    }
}
