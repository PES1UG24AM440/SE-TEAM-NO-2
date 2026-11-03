package com.bookstore.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(
    @NotEmpty
    @Size(max = 50)
    List<@Valid OrderLineRequest> items,

    @NotNull
    @Valid
    CustomerDetailsRequest customer,

    @Pattern(regexp = "success|failure")
    String payment_scenario
) {}