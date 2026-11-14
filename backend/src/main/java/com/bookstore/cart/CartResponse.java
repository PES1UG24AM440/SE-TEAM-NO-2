package com.bookstore.cart;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(List<CartItem> items, int itemCount, BigDecimal subtotal) {
    public record CartItem(Long book_id, String title, String author, BigDecimal unit_price,
                           int quantity, BigDecimal line_total, int stock) {}
}
