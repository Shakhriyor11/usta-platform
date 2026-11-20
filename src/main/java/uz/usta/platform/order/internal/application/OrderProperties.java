package uz.usta.platform.order.internal.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param offerBatchSize   bir vaqtda nechta eng yaqin ustaga taklif yuboriladi
 * @param offerTtl         usta taklifga javob berish muddati
 * @param searchTimeout    shu muddatda hech kim qabul qilmasa buyurtma EXPIRED
 * @param maxScheduleAhead buyurtmani qancha oldinga rejalashtirish mumkin
 * @param maxAttachments   bitta buyurtmaga rasm/video soni
 * @param maintenanceBatch fon vazifasi bir aylanishda qayta ishlaydigan buyurtmalar soni
 */
@ConfigurationProperties("app.order")
public record OrderProperties(int offerBatchSize, Duration offerTtl, Duration searchTimeout, Duration maxScheduleAhead,
                              int maxAttachments, int maintenanceBatch) {

    public OrderProperties {
        offerBatchSize = offerBatchSize <= 0 ? 5 : offerBatchSize;
        offerTtl = offerTtl == null ? Duration.ofMinutes(5) : offerTtl;
        searchTimeout = searchTimeout == null ? Duration.ofMinutes(15) : searchTimeout;
        maxScheduleAhead = maxScheduleAhead == null ? Duration.ofDays(30) : maxScheduleAhead;
        maxAttachments = maxAttachments <= 0 ? 5 : maxAttachments;
        maintenanceBatch = maintenanceBatch <= 0 ? 100 : maintenanceBatch;
    }
}
