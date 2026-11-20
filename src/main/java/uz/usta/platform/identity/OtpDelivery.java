package uz.usta.platform.identity;

/**
 * OTP kodini foydalanuvchiga yetkazish porti. Identity faqat shu interfeysni biladi; amalga oshirish
 * notification modulida (SMS provayderi). Bog'liqlik yo'nalishi: notification -> identity (aksincha emas),
 * shuning uchun notification boshqa modullar eventlarini tinglay oladi va sikl hosil bo'lmaydi.
 */
public interface OtpDelivery {

    /**
     * @param phone E.164, masalan {@code +998901234567}
     * @param text  tayyor, lokalizatsiya qilingan matn
     * @throws DeliveryFailedException yuborib bo'lmadi (provayder yoki tarmoq xatosi)
     */
    void deliver(String phone, String text);

    class DeliveryFailedException extends RuntimeException {
        public DeliveryFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
