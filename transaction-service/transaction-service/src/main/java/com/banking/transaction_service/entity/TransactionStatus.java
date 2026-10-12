package com.banking.transaction_service.entity;

/**
 * Transaction Lifecycle flow:
 * Pending -> Processing -> Completed (Clean transaction)
 *  -> PENDING_VERIFICATION (suspicious detected)
 *  -> COMPLETED (verified)
 *  -> FLAGGED (SAGA REFUND)
 *  -> FAILED
 *  -> FLAGGED
 */



public enum TransactionStatus {
    PENDING,
    PROCESSING,
    PENDING_VERIFICATION,
    COMPLETED,
    FAILED,
    FLAGGED
}
