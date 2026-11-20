package uz.usta.platform.payment.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CommissionMathTest {

    static PaymentProperties props(String percent) {
        return new PaymentProperties(new BigDecimal(percent), ZoneId.of("Asia/Tashkent"), 7);
    }

    @ParameterizedTest
    @CsvSource({
            "10, 200000, 20000.00",
            "10, 99999.99, 10000.00",   // 9999.999 -> HALF_UP
            "12.5, 100000, 12500.00",
            "0, 500000, 0.00"})
    void commissionIsRoundedToTiyin(String percent, String price, String expected) {
        assertThat(new CommissionCalculator(props(percent)).calculate(new BigDecimal(price)))
                .isEqualByComparingTo(expected).hasScaleOf(2);
    }

    @Test
    void defaultsAndValidation() {
        PaymentProperties p = new PaymentProperties(null, null, 0);
        assertThat(p.commissionPercent()).isEqualByComparingTo("10");
        assertThat(p.timezone()).isEqualTo(ZoneId.of("Asia/Tashkent"));
        assertThat(p.invoiceDueDays()).isEqualTo(7);
        assertThatThrownBy(() -> props("101")).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @CsvSource({
            // Toshkent = UTC+5: yakshanba 23:30 mahalliy hali o'sha hafta, dushanba 00:00 — yangi hafta
            "2026-11-22T18:29:59Z, 2026-11-16",
            "2026-11-22T19:00:00Z, 2026-11-23",
            "2026-11-18T10:00:00Z, 2026-11-16",
            "2026-11-15T19:00:00Z, 2026-11-16"})
    void weeklyPeriodsFollowTashkentTime(String instant, String expectedMonday) {
        BillingPeriods periods = new BillingPeriods(props("10"));
        assertThat(periods.periodOf(Instant.parse(instant))).isEqualTo(LocalDate.parse(expectedMonday));
    }

    @Test
    void dueDateIsWeekEndPlusGrace() {
        BillingPeriods periods = new BillingPeriods(props("10"));
        assertThat(periods.dueDate(LocalDate.parse("2026-11-16"))).isEqualTo(LocalDate.parse("2026-11-30"));
        assertThat(periods.isPeriodStart(LocalDate.parse("2026-11-17"))).isFalse();
    }
}
