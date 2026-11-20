package uz.usta.platform.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import uz.usta.platform.notification.SmsSender;

/** Test SMS adapteri: yuborilgan oxirgi xabarni raqam bo'yicha eslab qoladi (OTP kodini olish uchun). */
public class RecordingSmsSender implements SmsSender {

    private static final Pattern CODE = Pattern.compile("\\b(\\d{4,8})\\b");

    private final Map<String, String> lastByPhone = new ConcurrentHashMap<>();

    @Override
    public void send(String phone, String text) {
        lastByPhone.put(phone, text);
    }

    public String lastMessage(String phone) {
        return lastByPhone.get(phone);
    }

    public String lastCode(String phone) {
        String text = lastByPhone.get(phone);
        if (text == null) {
            throw new IllegalStateException("No SMS sent to " + phone);
        }
        Matcher m = CODE.matcher(text);
        if (!m.find()) {
            throw new IllegalStateException("No code in SMS: " + text);
        }
        return m.group(1);
    }
}
