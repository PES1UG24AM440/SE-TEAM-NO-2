package com.bookstore.admin.dto;

import java.math.BigDecimal;

/** Counts for the admin dashboard tiles (#73). */
public record AdminSummaryResponse(BookSummary books, UserSummary users, OrderSummary orders) {

    public record BookSummary(long total, long active, long inactive, long out_of_stock) {}

    public record UserSummary(long total, long customers, long sellers, long admins, long deactivated) {}

    public record OrderSummary(long total, long confirmed, long cancelled, BigDecimal confirmed_revenue,
                               BigDecimal refunded_total) {}
}
