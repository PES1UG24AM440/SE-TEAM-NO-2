package com.bookstore.orders;

public enum OrderStatus {
    CONFIRMED,
    CANCELLED;

    public boolean canTransitionTo(OrderStatus nextStatus) {
        return this == CONFIRMED && nextStatus == CANCELLED;
    }
}
