package uz.usta.platform.notification.internal.application;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import uz.usta.platform.notification.internal.persistence.NotificationStore;
import uz.usta.platform.notification.internal.persistence.NotificationStore.PushToken;
import uz.usta.platform.notification.internal.push.PushSender;

/** Push chaqiruvchi oqimdan tashqarida, har qurilmaga o'z tilida yuboriladi; eskirgan token o'chiriladi. */
class NotificationServicePushTest {

    private final NotificationStore store = mock(NotificationStore.class);
    private final PushSender push = mock(PushSender.class);
    private final NotificationService service;

    NotificationServicePushTest() {
        StaticMessageSource messages = new StaticMessageSource();
        NotificationType type = NotificationType.NEW_OFFER;
        messages.addMessage(type.titleKey(), Locale.forLanguageTag("uz"), "Yangi buyurtma");
        messages.addMessage(type.bodyKey(), Locale.forLanguageTag("uz"), "{0}");
        messages.addMessage(type.titleKey(), Locale.forLanguageTag("ru"), "Новый заказ");
        messages.addMessage(type.bodyKey(), Locale.forLanguageTag("ru"), "{0}");
        service = new NotificationService(store, new NotificationRenderer(messages), push, Clock.systemUTC());
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    void sendsToEveryDeviceInItsLanguageAndDropsInvalidTokens() {
        UUID userId = UUID.randomUUID();
        when(store.tokens(userId)).thenReturn(List.of(
                new PushToken("tok-uz", "ANDROID", "uz"),
                new PushToken("tok-ru", "IOS", "ru"),
                new PushToken("tok-old", "ANDROID", "uz")));
        doThrow(new PushSender.InvalidTokenException("gone"))
                .when(push).send(eq("tok-old"), anyString(), anyString(), eq(Map.of("orderId", "o1", "type", "NEW_OFFER")));

        // tranzaksiyadan tashqarida — darhol (lekin asinxron) yuboriladi
        service.pushOnly(userId, NotificationType.NEW_OFFER, List.of("Santexnik"), Map.of("orderId", "o1"));

        Map<String, String> data = Map.of("orderId", "o1", "type", "NEW_OFFER");
        verify(push, timeout(2000)).send("tok-uz", "Yangi buyurtma", "Santexnik", data);
        verify(push, timeout(2000)).send("tok-ru", "Новый заказ", "Santexnik", data);
        verify(store, timeout(2000)).deleteToken("tok-old");
    }
}
