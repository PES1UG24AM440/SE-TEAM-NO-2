package com.bookstore.orders.dto;

/** What an administrator sees when inspecting an order (#73): the order plus who placed it. */
public record AdminOrderResponse(
    String customer_name,
    String email,
    String phone,
    String shipping_address,
    OrderResponse order
) {}
