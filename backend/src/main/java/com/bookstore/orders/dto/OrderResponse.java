package com.bookstore.orders.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
    String order_id,
    String status,
    BigDecimal subtotal,
    BigDecimal shipping,
    BigDecimal tax,
    BigDecimal discount,
    BigDecimal total,
    List<OrderItemResponse> items,
    Instant created_at,
    /** Null until the order is cancelled (#71). */
    RefundResponse refund
) {
    public record OrderItemResponse(
        Long book_id,
        String title,
        Integer quantity,
        BigDecimal unit_price,
        BigDecimal line_total
    ) {}
}