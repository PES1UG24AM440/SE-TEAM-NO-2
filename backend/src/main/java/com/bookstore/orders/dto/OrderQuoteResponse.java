package com.bookstore.orders.dto;

import java.math.BigDecimal;
import java.util.List;

public record OrderQuoteResponse(
    List<Item> items,
    int item_count,
    BigDecimal subtotal,
    BigDecimal shipping,
    BigDecimal tax,
    BigDecimal discount,
    BigDecimal total
) {
    public record Item(
        Long book_id,
        String title,
        String author,
        int quantity,
        BigDecimal unit_price,
        BigDecimal line_total
    ) {}
}
