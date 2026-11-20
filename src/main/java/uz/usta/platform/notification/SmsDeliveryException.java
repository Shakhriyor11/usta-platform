package uz.usta.platform.notification;

/** SMS provayderiga yuborib bo'lmadi. */
public class SmsDeliveryException extends RuntimeException {

    public SmsDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
