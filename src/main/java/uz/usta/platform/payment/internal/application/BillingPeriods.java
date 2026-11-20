package uz.usta.platform.payment.internal.application;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import org.springframework.stereotype.Component;

/** Hisob davri — hafta: dushanba 00:00 dan keyingi dushanba 00:00 gacha ({@code app.payment.timezone}). */
@Component
public class BillingPeriods {

    private final PaymentProperties props;

    public BillingPeriods(PaymentProperties props) {
        this.props = props;
    }

    /** Vaqt tegishli bo'lgan haftaning dushanbasi. */
    public LocalDate periodOf(Instant at) {
        return at.atZone(props.timezone()).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public boolean isPeriodStart(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.MONDAY;
    }

    public LocalDate dueDate(LocalDate period) {
        return period.plusWeeks(1).plusDays(props.invoiceDueDays());
    }
}
