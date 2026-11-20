package uz.usta.platform.payment.internal.domain;

/** ACCRUED (hafta davomida) -> INVOICED (hafta yopildi) -> PAID; qayta hisoblash yoki bekor qilishda — CANCELLED. */
public enum CommissionStatus {
    ACCRUED,
    INVOICED,
    PAID,
    CANCELLED
}
