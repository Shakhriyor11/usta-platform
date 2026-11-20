package uz.usta.platform.payment.internal.domain;

/** Ledger yozuvi turi. Ishora: CHARGE — manfiy (usta qarzi), REVERSAL va PAYMENT — musbat. */
public enum TransactionType {
    COMMISSION_CHARGE,
    COMMISSION_REVERSAL,
    COMMISSION_PAYMENT,
    ADJUSTMENT
}
