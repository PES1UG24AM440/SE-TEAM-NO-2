package com.bookstore.orders;

/**
 * Payment is simulated (SRS §1), so an eligible refund is completed as soon as the order is cancelled.
 * A real payment gateway would add PENDING and FAILED here.
 */
public enum RefundStatus {
    REFUNDED
}
