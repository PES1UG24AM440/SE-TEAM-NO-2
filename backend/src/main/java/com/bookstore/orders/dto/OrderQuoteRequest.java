package com.bookstore.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record OrderQuoteRequest(
    @NotEmpty
    @Size(max = 50)
    List<@Valid OrderLineRequest> items
) {}
