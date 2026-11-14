package com.bookstore.orders.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
    List<CartItemResponse> items,
    BigDecimal subtotal
) {
    public record CartItemResponse(
        Long book_id,
        String title,
        String author,
        String category,
        String description,
        Integer quantity,
        Integer stock,
        BigDecimal unit_price,
        BigDecimal line_total
    ) {}
}
