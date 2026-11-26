package uz.usta.platform.notification.internal.application;

import java.util.UUID;
import org.springframework.stereotype.Component;
import uz.usta.platform.identity.PushTokenRevoker;

@Component
class LogoutPushTokenRevoker implements PushTokenRevoker {

    private final NotificationService notifications;

    LogoutPushTokenRevoker(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Override
    public void revoke(UUID userId, String pushToken) {
        notifications.unregisterToken(userId, pushToken);
    }
}
