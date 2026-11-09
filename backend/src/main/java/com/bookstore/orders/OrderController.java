package com.bookstore.orders;

import com.bookstore.orders.dto.CreateOrderRequest;
import com.bookstore.orders.dto.OrderResponse;
import com.bookstore.orders.dto.OrderQuoteRequest;
import com.bookstore.orders.dto.OrderQuoteResponse;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderQuoteService quoteService;

    public OrderController(OrderService orderService, OrderQuoteService quoteService) {
        this.orderService = orderService;
        this.quoteService = quoteService;
    }

    @PostMapping("/quote")
    public ResponseEntity<OrderQuoteResponse> quote(@Valid @RequestBody OrderQuoteRequest request) {
        return ResponseEntity.ok(quoteService.quote(request));
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(
                value = "Idempotency-Key",
                required = false
            ) String idempotencyKey) {

        OrderResponse response =
                orderService.createOrder(request, idempotencyKey);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable String orderId) {
        return ResponseEntity.ok(orderService.cancelOrder(orderId));
    }
}
