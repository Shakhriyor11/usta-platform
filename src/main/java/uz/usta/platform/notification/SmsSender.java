package uz.usta.platform.notification;

/**
 * SMS yuborish adapteri. Provayder {@code app.sms.provider} orqali tanlanadi:
 * {@code log} (standart, lokal — matn logga yoziladi) yoki {@code eskiz} (Eskiz.uz).
 */
public interface SmsSender {

    /**
     * Sinxron yuboradi.
     *
     * @param phone E.164 formatida, masalan {@code +998901234567}
     * @throws SmsDeliveryException provayder xatosi yoki tarmoq muammosi
     */
    void send(String phone, String text);
}
