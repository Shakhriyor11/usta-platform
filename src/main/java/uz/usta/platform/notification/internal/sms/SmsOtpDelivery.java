package uz.usta.platform.notification.internal.sms;

import org.springframework.stereotype.Component;
import uz.usta.platform.identity.OtpDelivery;
import uz.usta.platform.notification.SmsDeliveryException;
import uz.usta.platform.notification.SmsSender;

/** Identity'ning OTP portini SMS orqali amalga oshiradi. */
@Component
class SmsOtpDelivery implements OtpDelivery {

    private final SmsSender sms;

    SmsOtpDelivery(SmsSender sms) {
        this.sms = sms;
    }

    @Override
    public void deliver(String phone, String text) {
        try {
            sms.send(phone, text);
        } catch (SmsDeliveryException e) {
            throw new DeliveryFailedException(e.getMessage(), e);
        }
    }
}
