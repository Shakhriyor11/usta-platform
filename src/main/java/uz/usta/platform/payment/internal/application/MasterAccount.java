package uz.usta.platform.payment.internal.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.usta.platform.payment.internal.domain.CommissionReport;
import uz.usta.platform.payment.internal.domain.CommissionStatus;
import uz.usta.platform.payment.internal.persistence.CommissionReportRepository;
import uz.usta.platform.payment.internal.persistence.LedgerRepository;

/**
 * Ustaning platforma bilan hisobi: qulf, ortiqcha to'lov (kredit) va to'lanishi kerak summa.
 *
 * <p>Kredit = ledger balansi + ochiq (ACCRUED/INVOICED) komissiyalar. Odatda 0; to'langan komissiya keyin bekor
 * qilinsa yoki kamaytirilsa — musbat bo'ladi va keyingi qarzdan chegiriladi.
 */
@Component
class MasterAccount {

    private final CommissionReportRepository reports;
    private final LedgerRepository ledger;
    private final JdbcClient jdbc;

    MasterAccount(CommissionReportRepository reports, LedgerRepository ledger, JdbcClient jdbc) {
        this.reports = reports;
        this.ledger = ledger;
        this.jdbc = jdbc;
    }

    /**
     * Ustaning hisobini tranzaksiya oxirigacha qulflaydi: hisoblash, bekor qilish, davr yopish va to'lov qabul
     * qilish bitta usta uchun ketma-ket bajariladi (masalan parallel ikki to'lov bitta qarzni ikki marta yozmaydi).
     * Hisobotlar o'qilishidan OLDIN chaqirilishi shart.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    void lock(UUID masterId) {
        jdbc.sql("select pg_advisory_xact_lock(hashtextextended(:key, 0))")
                .param("key", "payment:" + masterId)
                .query((rs, i) -> 0)
                .list();
    }

    /** Ortiqcha to'langan, hali hech bir komissiyaga hisoblanmagan summa (manfiy bo'lmaydi). */
    BigDecimal credit(UUID masterId) {
        return ledger.balance(masterId).add(reports.sumOpen(masterId)).max(BigDecimal.ZERO);
    }

    /** Shu summadan kredit chegirilgandan keyin to'lanishi kerak qismi. */
    BigDecimal due(UUID masterId, BigDecimal invoiced) {
        return invoiced.subtract(credit(masterId)).max(BigDecimal.ZERO);
    }

    /** Kredit to'liq qoplaydigan INVOICED komissiyalarni (eskisidan boshlab) PAID qiladi. Qulf ostida chaqiriladi. */
    @Transactional(propagation = Propagation.MANDATORY)
    void applyCredit(UUID masterId, Instant now) {
        BigDecimal credit = credit(masterId);
        if (credit.signum() <= 0) {
            return;
        }
        for (CommissionReport r : reports.findByMasterStatus(masterId, CommissionStatus.INVOICED)) {
            if (!r.isOnHold() && r.getAmount().compareTo(credit) <= 0) {
                credit = credit.subtract(r.getAmount());
                r.markPaid(now);
            }
        }
        reports.flush();
    }
}
