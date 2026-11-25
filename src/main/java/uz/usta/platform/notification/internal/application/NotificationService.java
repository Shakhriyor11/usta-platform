package uz.usta.platform.notification.internal.application;

import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import uz.usta.platform.notification.internal.persistence.NotificationStore;
import uz.usta.platform.notification.internal.persistence.NotificationStore.PushToken;
import uz.usta.platform.notification.internal.persistence.NotificationStore.Row;
import uz.usta.platform.notification.internal.push.PushSender;
import uz.usta.platform.shared.error.BusinessException;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    /** FCM'ga bir vaqtdagi so'rovlar chegarasi. */
    private static final int MAX_CONCURRENT_PUSHES = 32;
    private static final Duration PUSH_SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    private final NotificationStore store;
    private final NotificationRenderer renderer;
    private final PushSender push;
    private final Clock clock;
    private final SimpleAsyncTaskExecutor pushExecutor = createPushExecutor();

    NotificationService(NotificationStore store, NotificationRenderer renderer, PushSender push, Clock clock) {
        this.store = store;
        this.renderer = renderer;
        this.push = push;
        this.clock = clock;
    }

    public record View(UUID id, NotificationType type, String title, String body, Map<String, String> data,
                       boolean read, Instant createdAt) {
    }

    /**
     * In-app bildirishnoma yozadi (idempotent) va tranzaksiya commit bo'lgach push yuboradi.
     * Push xatosi bildirishnomani bekor qilmaydi.
     */
    @Transactional
    public void notify(UUID sourceEventId, UUID userId, NotificationType type, List<String> args,
                       Map<String, String> data) {
        NotificationRenderer.Text text = renderer.render(type, args, NotificationRenderer.DEFAULT);
        store.insert(sourceEventId, userId, type.name(), text.title(), text.body(), args, data)
                .ifPresent(id -> afterCommit(() -> sendPush(userId, type, args, data)));
    }

    /** Faqat push (in-app yozuvsiz), commit'dan keyin. At-least-once: kamdan-kam takror push bo'lishi mumkin. */
    public void pushOnly(UUID userId, NotificationType type, List<String> args, Map<String, String> data) {
        afterCommit(() -> sendPush(userId, type, args, data));
    }

    /**
     * Push tashqi HTTP chaqiruv (FCM) — outbox relay oqimini va uning DB ulanishini band qilmasligi uchun
     * alohida virtual thread'da yuboriladi. Limitga yetilsa chaqiruvchi kutadi (backpressure).
     */
    private void sendPush(UUID userId, NotificationType type, List<String> args, Map<String, String> payload) {
        try {
            pushExecutor.execute(() -> deliverPush(userId, type, args, payload));
        } catch (TaskRejectedException e) {
            log.warn("Push skipped for user {} (shutting down)", userId);
        }
    }

    private void deliverPush(UUID userId, NotificationType type, List<String> args, Map<String, String> payload) {
        try {
            // Mobil ilova bosilganda qaysi ekranni ochishni shu bo'yicha tanlaydi.
            Map<String, String> data = new HashMap<>(payload);
            data.put("type", type.name());
            Map<String, NotificationRenderer.Text> byLocale = new HashMap<>();
            for (PushToken t : store.tokens(userId)) {
                NotificationRenderer.Text text = byLocale.computeIfAbsent(t.locale(),
                        l -> renderer.render(type, args, Locale.forLanguageTag(l)));
                try {
                    push.send(t.token(), text.title(), text.body(), data);
                } catch (PushSender.InvalidTokenException e) {
                    store.deleteToken(t.token());
                    log.info("Removed invalid push token for user {}", userId);
                } catch (RuntimeException e) {
                    log.warn("Push failed for user {}: {}", userId, e.getMessage());
                }
            }
        } catch (RuntimeException e) {
            log.warn("Push delivery failed for user {}: {}", userId, e.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        pushExecutor.close(); // navbatdagi push'larni PUSH_SHUTDOWN_TIMEOUT gacha kutadi
    }

    private static SimpleAsyncTaskExecutor createPushExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("push-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(MAX_CONCURRENT_PUSHES);
        executor.setTaskTerminationTimeout(PUSH_SHUTDOWN_TIMEOUT.toMillis());
        return executor;
    }

    @Transactional(readOnly = true)
    public List<View> page(UUID userId, boolean unreadOnly, int page, int size) {
        Locale locale = LocaleContextHolder.getLocale();
        List<Row> rows = store.page(userId, unreadOnly, size, (long) page * size);
        return rows.stream().map(r -> {
            NotificationType type = NotificationType.valueOf(r.type());
            NotificationRenderer.Text text = renderer.render(type, r.args(), locale);
            return new View(r.id(), type, text.title(), text.body(), r.data(), r.read(), r.createdAt());
        }).toList();
    }

    @Transactional(readOnly = true)
    public long count(UUID userId, boolean unreadOnly) {
        return store.count(userId, unreadOnly);
    }

    @Transactional
    public void markRead(UUID userId, UUID id) {
        if (!store.markRead(userId, id, Instant.now(clock))) {
            throw BusinessException.notFound("Notification", id);
        }
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return store.markAllRead(userId, Instant.now(clock));
    }

    @Transactional
    public void registerToken(UUID userId, String token, String platform, Locale locale) {
        String lang = switch (locale.getLanguage()) {
            case "ru", "en" -> locale.getLanguage();
            default -> "uz";
        };
        store.upsertToken(userId, token, platform, lang);
    }

    @Transactional
    public void unregisterToken(UUID userId, String token) {
        store.deleteToken(userId, token);
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
