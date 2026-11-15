package com.bookstore.orders;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/**
 * Refund policy (#71, SRS OBS-F-014). The SRS says the cancellation-eligibility and refund rules
 * are configurable and still to be confirmed by the Product Owner, so every rule is a setting in
 * application.properties. The defaults refund exactly what the customer paid:
 *
 *   bookstore.refund.refund-shipping=true          shipping is refunded
 *   bookstore.refund.cancellation-fee-rate=0       no fee (0.10 = 10% of the refundable amount)
 *   bookstore.refund.cancellation-window-hours=0   0 = any CONFIRMED order can be cancelled
 *
 * Tax and the discount are part of the stored order total, so they are returned in proportion:
 * the refund starts from orders.total (subtotal + shipping + tax - discount) as charged at checkout.
 */
@Component
public class RefundPolicy {

    @Value("${bookstore.refund.refund-shipping:true}")
    private boolean refundShipping;

    @Value("${bookstore.refund.cancellation-fee-rate:0}")
    private BigDecimal cancellationFeeRate;

    @Value("${bookstore.refund.cancellation-window-hours:0}")
    private long cancellationWindowHours;

    @PostConstruct
    void validate() {
        if (cancellationFeeRate.signum() < 0 || cancellationFeeRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalStateException("bookstore.refund.cancellation-fee-rate must be between 0 and 1.");
        }
        if (cancellationWindowHours < 0) {
            throw new IllegalStateException("bookstore.refund.cancellation-window-hours cannot be negative.");
        }
    }

    /** Eligibility: CONFIRMED orders inside the cancellation window (if one is configured). */
    public boolean isWithinCancellationWindow(Order order, Instant now) {
        if (cancellationWindowHours == 0 || order.getCreatedAt() == null) {
            return true;
        }
        return !now.isAfter(order.getCreatedAt().plus(Duration.ofHours(cancellationWindowHours)));
    }

    public long cancellationWindowHours() {
        return cancellationWindowHours;
    }

    /** Builds the refund for an order that is being cancelled, using only its stored amounts. */
    public Refund refundFor(Order order) {
        BigDecimal paid = money(order.getTotal());
        BigDecimal shippingWithheld = refundShipping ? money(BigDecimal.ZERO) : money(order.getShipping().min(paid));
        BigDecimal refundable = paid.subtract(shippingWithheld).max(BigDecimal.ZERO);
        BigDecimal fee = money(refundable.multiply(cancellationFeeRate));
        BigDecimal amount = money(refundable.subtract(fee));
        RefundType type = amount.compareTo(paid) == 0 ? RefundType.FULL : RefundType.PARTIAL;

        return new Refund(order, paid, shippingWithheld, fee, amount, type, RefundStatus.REFUNDED);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
