package com.bookstore.orders.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderLineRequest(
    @NotNull
    @Positive
    Long book_id,

    @NotNull
    @Positive
    Integer quantity
) {}