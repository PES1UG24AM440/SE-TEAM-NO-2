
package com.bookstore.orders;

import com.bookstore.orders.dto.CreateOrderRequest;
import com.bookstore.orders.dto.OrderResponse;

import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final IdempotencyKeyRepository idempotencyRepository;
    private final OrderCreationService orderCreationService;

    public OrderService(
            IdempotencyKeyRepository idempotencyRepository,
            OrderCreationService orderCreationService) {
        this.idempotencyRepository = idempotencyRepository;
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
            order.getStatus(),
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