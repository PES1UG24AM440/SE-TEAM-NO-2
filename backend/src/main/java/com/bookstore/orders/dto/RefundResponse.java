package com.bookstore.orders.dto;

import com.bookstore.orders.Refund;

import java.math.BigDecimal;
import java.time.Instant;

/** Refund details shown with a cancelled order (#71). */
public record RefundResponse(
    BigDecimal amount,
    String status,
    String type,
    BigDecimal order_total,
    BigDecimal shipping_withheld,
    BigDecimal cancellation_fee,
    Instant created_at
) {
    public static RefundResponse from(Refund refund) {
        return new RefundResponse(
            refund.getAmount(),
            refund.getStatus().name(),
            refund.getRefundType().name(),
            refund.getOrderTotal(),
            refund.getShippingWithheld(),
            refund.getCancellationFee(),
            refund.getCreatedAt()
        );
    }
}
