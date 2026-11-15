package com.bookstore.orders;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The refund for a cancelled order (#71). order_id is UNIQUE, so the database itself makes a
 * second refund for the same order impossible. Amounts come from the order's stored totals,
 * never from today's book prices.
 */
@Entity
@Table(name = "refunds")
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Order order;

    /** What the customer paid (orders.total at checkout). */
    @Column(name = "order_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal orderTotal;

    @Column(name = "shipping_withheld", nullable = false, precision = 10, scale = 2)
    private BigDecimal shippingWithheld;

    @Column(name = "cancellation_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal cancellationFee;

    /** Amount returned to the customer. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_type", nullable = false, length = 10)
    private RefundType refundType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Refund() {}

    public Refund(
            Order order,
            BigDecimal orderTotal,
            BigDecimal shippingWithheld,
            BigDecimal cancellationFee,
            BigDecimal amount,
            RefundType refundType,
            RefundStatus status) {
        this.order = order;
        this.orderTotal = orderTotal;
        this.shippingWithheld = shippingWithheld;
        this.cancellationFee = cancellationFee;
        this.amount = amount;
        this.refundType = refundType;
        this.status = status;
    }

    public Long getId() { return id; }
    public Order getOrder() { return order; }
    public BigDecimal getOrderTotal() { return orderTotal; }
    public BigDecimal getShippingWithheld() { return shippingWithheld; }
    public BigDecimal getCancellationFee() { return cancellationFee; }
    public BigDecimal getAmount() { return amount; }
    public RefundType getRefundType() { return refundType; }
    public RefundStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
