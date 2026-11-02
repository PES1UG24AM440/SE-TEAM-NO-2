package com.bookstore.orders;

import jakarta.persistence.*;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

    @Id
    @Column(name = "`key`", length = 120)
    private String key;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    protected IdempotencyKey() {}

    public IdempotencyKey(String key, Order order) {
        this.key = key;
        this.order = order;
    }

    public String getKey() { return key; }
    public Order getOrder() { return order; }

    public void setKey(String key) { this.key = key; }
    public void setOrder(Order order) { this.order = order; }
}