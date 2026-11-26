package uz.usta.platform.identity;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Boshqa modullar uchun foydalanuvchi ma'lumotlari (faqat o'qish). */
public interface UserDirectory {

    /** @return userId -> telefon raqam (topilmaganlar natijada bo'lmaydi) */
    Map<UUID, String> phones(Collection<UUID> userIds);

    /** Hisob o'chirilgan (anonimlashtirilgan) — unga yangi ma'lumot yozilmaydi. */
    boolean isDeleted(UUID userId);
}
