package com.bookstore.orders.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartQuantityRequest(
    @NotNull
    @Min(1)
    Integer quantity
) {}
