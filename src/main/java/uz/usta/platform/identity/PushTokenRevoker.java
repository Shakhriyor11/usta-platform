package uz.usta.platform.identity;

import java.util.UUID;

/** Chiqish (logout) paytida shu qurilmaga push yuborishni to'xtatish — notification moduli amalga oshiradi. */
public interface PushTokenRevoker {

    /** Token shu foydalanuvchiniki bo'lmasa — hech narsa qilmaydi. */
    void revoke(UUID userId, String pushToken);
}
