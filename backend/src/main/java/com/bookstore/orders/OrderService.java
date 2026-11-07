
package com.bookstore.orders;

import com.bookstore.books.BookRepository;
import com.bookstore.orders.dto.CreateOrderRequest;
import com.bookstore.orders.dto.OrderResponse;

import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final BookRepository bookRepository;
    private final IdempotencyKeyRepository idempotencyRepository;
    private final OrderRepository orderRepository;
    private final OrderCreationService orderCreationService;

    public OrderService(
            BookRepository bookRepository,
            IdempotencyKeyRepository idempotencyRepository,
            OrderRepository orderRepository,
            OrderCreationService orderCreationService) {
        this.bookRepository = bookRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.orderRepository = orderRepository;
        this.orderCreationService = orderCreationService;
    }

    public OrderResponse createOrder(
            CreateOrderRequest request,
            String idempotencyKey) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new OrderException(
                400,
                "invalid_idempotency_key",
                "Idempotency-Key cannot be blank."
            );
        }

        if (idempotencyKey.length() > 120) {
            throw new OrderException(
                400,
                "invalid_idempotency_key",
                "Idempotency-Key cannot exceed 120 characters."
            );
        }

        var previous = findExistingOrder(idempotencyKey);

        if (previous != null) {
            return previous;
        }

        try {
            return orderCreationService.createOrder(
                request,
                idempotencyKey
            );
        } catch (DataIntegrityViolationException exception) {
            // The separate transactional service has rolled back.
            // Check whether another request created this same key.
            var existing = findExistingOrder(idempotencyKey);

            if (existing != null) {
                return existing;
            }

            throw new OrderException(
                409,
                "order_conflict",
                "The order could not be completed. Please retry."
            );
        }
    }

    @Transactional
    public OrderResponse cancelOrder(String orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new OrderException(
                404,
                "order_not_found",
                "Order " + orderId + " was not found."
            ));

        order.transitionTo(OrderStatus.CANCELLED);

        for (OrderItem item : order.getItems()) {
            int updated = bookRepository.restoreStock(
                item.getBook().getId(),
                item.getQuantity()
            );

            if (updated != 1) {
                throw new IllegalStateException(
                    "Could not restore stock for book " + item.getBook().getId()
                );
            }
        }

        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse findExistingOrder(String key) {
        return idempotencyRepository.findByKey(Objects.requireNonNull(key))
            .map(idempotencyKey -> Objects.requireNonNull(idempotencyKey).getOrder())
            .map(this::toResponse)
            .orElse(null);
    }

    private OrderResponse toResponse(Order order) {
        return new OrderResponse(
            order.getId(),
            order.getStatus().name(),
            order.getSubtotal(),
            order.getShipping(),
            order.getTax(),
            order.getDiscount(),
            order.getTotal(),
            order.getItems().stream()
                .map(item -> new OrderResponse.OrderItemResponse(
                    item.getBook().getId(),
                    item.getTitle(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.getLineTotal()
                ))
                .toList()
        );
    }
}