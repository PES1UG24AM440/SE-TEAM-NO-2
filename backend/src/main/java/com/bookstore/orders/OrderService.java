package com.bookstore.orders;

import com.bookstore.books.BookRepository;
import com.bookstore.orders.dto.CreateOrderRequest;
import com.bookstore.orders.dto.OrderResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final BookRepository bookRepository;
    private final IdempotencyKeyRepository idempotencyRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final OrderRepository orderRepository;
    private final OrderCreationService orderCreationService;

    public OrderService(
            BookRepository bookRepository,
            IdempotencyKeyRepository idempotencyRepository,
            InventoryMovementRepository inventoryMovementRepository,
            OrderRepository orderRepository,
            OrderCreationService orderCreationService) {
        this.bookRepository = bookRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.inventoryMovementRepository = inventoryMovementRepository;
        this.orderRepository = orderRepository;
        this.orderCreationService = orderCreationService;
    }

    public OrderResponse createOrder(
            CreateOrderRequest request,
            String idempotencyKey,
            String ownerSessionId) {

        if (ownerSessionId == null || ownerSessionId.isBlank()) {
            throw new IllegalArgumentException("Order owner session is required.");
        }

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

        var previous = findExistingOrder(idempotencyKey, ownerSessionId);
        if (previous != null) {
            return previous;
        }

        try {
            return orderCreationService.createOrder(
                request,
                idempotencyKey,
                ownerSessionId
            );
        } catch (DataIntegrityViolationException exception) {
            var existing = findExistingOrder(idempotencyKey, ownerSessionId);
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
    public OrderResponse cancelOrder(String orderId, String ownerSessionId) {
        Order order = orderRepository
            .findByIdAndOwnerSessionIdForUpdate(orderId, ownerSessionId)
            .orElseThrow(() -> orderNotFound(orderId));
        return cancelOrder(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(String orderId, String ownerSessionId) {
        return orderRepository.findByIdAndOwnerSessionId(orderId, ownerSessionId)
            .map(this::toResponse)
            .orElseThrow(() -> orderNotFound(orderId));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrders(String ownerSessionId) {
        return orderRepository.findByOwnerSessionIdOrderByIdDesc(ownerSessionId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse findExistingOrder(String key, String ownerSessionId) {
        return idempotencyRepository.findByKey(Objects.requireNonNull(key))
            .map(idempotencyKey -> Objects.requireNonNull(idempotencyKey).getOrder())
            .map(order -> {
                if (!Objects.equals(order.getOwnerSessionId(), ownerSessionId)) {
                    throw new OrderException(
                        409,
                        "idempotency_key_conflict",
                        "This Idempotency-Key is already associated with another order."
                    );
                }
                return order;
            })
            .map(this::toResponse)
            .orElse(null);
    }

    private OrderResponse cancelOrder(Order order) {
        order.transitionTo(OrderStatus.CANCELLED);

        var restockMovements = new ArrayList<InventoryMovement>();
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

            restockMovements.add(new InventoryMovement(
                item.getBook(),
                order,
                item.getQuantity(),
                InventoryMovementType.ORDER_CANCELLATION_RESTOCK
            ));
        }

        inventoryMovementRepository.saveAllAndFlush(restockMovements);
        return toResponse(order);
    }

    private OrderException orderNotFound(String orderId) {
        return new OrderException(
            404,
            "order_not_found",
            "Order " + orderId + " was not found."
        );
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
