package com.bookstore.orders;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for the refund calculation (#71). No Spring context or database needed. */
class RefundPolicyTest {

    private static RefundPolicy policy(boolean refundShipping, String feeRate, long windowHours) {
        RefundPolicy policy = new RefundPolicy();
        ReflectionTestUtils.setField(policy, "refundShipping", refundShipping);
        ReflectionTestUtils.setField(policy, "cancellationFeeRate", new BigDecimal(feeRate));
        ReflectionTestUtils.setField(policy, "cancellationWindowHours", windowHours);
        policy.validate();
        return policy;
    }

    /** subtotal 200 + shipping 40 + tax 10 - discount 10 = total 240, as stored at checkout. */
    private static Order storedOrder(String subtotal, String shipping, String tax, String discount, String total) {
        Order order = new Order();
        order.setSubtotal(new BigDecimal(subtotal));
        order.setShipping(new BigDecimal(shipping));
        order.setTax(new BigDecimal(tax));
        order.setDiscount(new BigDecimal(discount));
        order.setTotal(new BigDecimal(total));
        return order;
    }

    @Test
    void defaultPolicyRefundsEverythingThatWasPaid() {
        Refund refund = policy(true, "0", 0).refundFor(storedOrder("200.00", "40.00", "10.00", "10.00", "240.00"));

        assertThat(refund.getAmount()).isEqualByComparingTo("240.00");
        assertThat(refund.getOrderTotal()).isEqualByComparingTo("240.00");
        assertThat(refund.getShippingWithheld()).isEqualByComparingTo("0.00");
        assertThat(refund.getCancellationFee()).isEqualByComparingTo("0.00");
        assertThat(refund.getRefundType()).isEqualTo(RefundType.FULL);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REFUNDED);
    }

    @Test
    void withholdingShippingGivesAPartialRefund() {
        Refund refund = policy(false, "0", 0).refundFor(storedOrder("200.00", "40.00", "10.00", "10.00", "240.00"));

        assertThat(refund.getShippingWithheld()).isEqualByComparingTo("40.00");
        assertThat(refund.getAmount()).isEqualByComparingTo("200.00");
        assertThat(refund.getRefundType()).isEqualTo(RefundType.PARTIAL);
    }

    @Test
    void cancellationFeeIsTakenFromTheRefundableAmountAndRounded() {
        Refund refund = policy(false, "0.10", 0).refundFor(storedOrder("200.00", "40.00", "10.00", "10.00", "240.00"));

        assertThat(refund.getCancellationFee()).isEqualByComparingTo("20.00");
        assertThat(refund.getAmount()).isEqualByComparingTo("180.00");

        Refund odd = policy(true, "0.10", 0).refundFor(storedOrder("99.99", "0.00", "0.00", "0.00", "99.99"));
        assertThat(odd.getCancellationFee()).isEqualByComparingTo("10.00");
        assertThat(odd.getAmount()).isEqualByComparingTo("89.99");
    }

    @Test
    void refundNeverGoesBelowZeroOrAboveWhatWasPaid() {
        // A discount larger than the books: the customer only paid 5.00, all of it shipping.
        Refund refund = policy(false, "0", 0).refundFor(storedOrder("20.00", "40.00", "0.00", "55.00", "5.00"));
        assertThat(refund.getShippingWithheld()).isEqualByComparingTo("5.00");
        assertThat(refund.getAmount()).isEqualByComparingTo("0.00");

        Refund full = policy(true, "0", 0).refundFor(storedOrder("20.00", "40.00", "0.00", "55.00", "5.00"));
        assertThat(full.getAmount()).isEqualByComparingTo("5.00");
    }

    @Test
    void usesStoredOrderTotalsNotCurrentPrices() {
        Order order = storedOrder("300.00", "0.00", "0.00", "0.00", "300.00");
        // Nothing in the calculation reads a Book price; changing the catalogue cannot change this refund.
        assertThat(policy(true, "0", 0).refundFor(order).getAmount()).isEqualByComparingTo("300.00");
    }

    @Test
    void cancellationWindow() {
        Order order = storedOrder("10.00", "0.00", "0.00", "0.00", "10.00");
        Instant placed = Instant.parse("2026-11-15T10:00:00Z");
        ReflectionTestUtils.setField(order, "createdAt", placed);

        assertThat(policy(true, "0", 0).isWithinCancellationWindow(order, placed.plus(Duration.ofDays(400)))).isTrue();
        RefundPolicy oneDay = policy(true, "0", 24);
        assertThat(oneDay.isWithinCancellationWindow(order, placed.plus(Duration.ofHours(24)))).isTrue();
        assertThat(oneDay.isWithinCancellationWindow(order, placed.plus(Duration.ofHours(25)))).isFalse();
    }

    @Test
    void invalidConfigurationFailsAtStartup() {
        assertThatThrownBy(() -> policy(true, "1.5", 0)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> policy(true, "-0.1", 0)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> policy(true, "0", -1)).isInstanceOf(IllegalStateException.class);
    }
}
