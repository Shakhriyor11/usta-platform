package uz.usta.platform.order.internal.application;

import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param offerBatchSize      bir vaqtda nechta eng yaqin ustaga taklif yuboriladi
 * @param offerTtl            usta taklifga javob berish muddati
 * @param searchTimeout       shu muddatda hech kim qabul qilmasa buyurtma EXPIRED (rejalashtirilganida — eng kamida)
 * @param maxScheduleAhead    buyurtmani qancha oldinga rejalashtirish mumkin
 * @param maxAttachments      bitta buyurtmaga rasm/video soni
 * @param maintenanceBatch    fon vazifasi bir aylanishda qayta ishlaydigan buyurtmalar soni
 * @param scheduledSearchLead rejalashtirilgan buyurtma qidiruvi belgilangan vaqtdan shuncha oldin tugaydi
 * @param busyHorizon         shu muddat ichida boshlanadigan buyurtmalarga band ustalar taklif olmaydi
 * @param createLimit         bir mijoz {@code createWindow} ichida yarata oladigan buyurtmalar soni
 */
@ConfigurationProperties("app.order")
public record OrderProperties(int offerBatchSize, Duration offerTtl, Duration searchTimeout, Duration maxScheduleAhead,
                              int maxAttachments, int maintenanceBatch, Duration scheduledSearchLead,
                              Duration busyHorizon, int createLimit, Duration createWindow) {

    public OrderProperties {
        offerBatchSize = offerBatchSize <= 0 ? 5 : offerBatchSize;
        offerTtl = offerTtl == null ? Duration.ofMinutes(5) : offerTtl;
        searchTimeout = searchTimeout == null ? Duration.ofMinutes(15) : searchTimeout;
        maxScheduleAhead = maxScheduleAhead == null ? Duration.ofDays(30) : maxScheduleAhead;
        maxAttachments = maxAttachments <= 0 ? 5 : maxAttachments;
        maintenanceBatch = maintenanceBatch <= 0 ? 100 : maintenanceBatch;
        scheduledSearchLead = scheduledSearchLead == null ? Duration.ofHours(1) : scheduledSearchLead;
        busyHorizon = busyHorizon == null ? Duration.ofHours(2) : busyHorizon;
        createLimit = createLimit <= 0 ? 20 : createLimit;
        createWindow = createWindow == null ? Duration.ofHours(1) : createWindow;
    }

    /** Qidiruv muddati: {@code searchTimeout}, rejalashtirilgan buyurtmada — vaqtidan {@code scheduledSearchLead} oldin. */
    Instant searchDeadline(Instant now, Instant scheduledAt) {
        Instant minimum = now.plus(searchTimeout);
        if (scheduledAt == null) {
            return minimum;
        }
        Instant beforeVisit = scheduledAt.minus(scheduledSearchLead);
        return beforeVisit.isAfter(minimum) ? beforeVisit : minimum;
    }

    boolean startsSoon(Instant now, Instant scheduledAt) {
        return scheduledAt == null || !scheduledAt.isAfter(now.plus(busyHorizon));
    }
}
