package com.bookstore.orders.dto;

import java.math.BigDecimal;
import java.util.List;

public record OrderResponse(
    String order_id,
    String status,
    BigDecimal subtotal,
    BigDecimal shipping,
    BigDecimal tax,
    BigDecimal discount,
    BigDecimal total,
    List<OrderItemResponse> items
) {
    public record OrderItemResponse(
        Long book_id,
        String title,
        Integer quantity,
        BigDecimal unit_price,
        BigDecimal line_total
    ) {}
}