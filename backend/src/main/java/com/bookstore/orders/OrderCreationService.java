
package com.bookstore.orders;

import com.bookstore.books.Book;
import com.bookstore.books.BookRepository;
import com.bookstore.orders.dto.CreateOrderRequest;
import com.bookstore.orders.dto.CustomerDetailsRequest;
import com.bookstore.orders.dto.OrderLineRequest;
import com.bookstore.orders.dto.OrderResponse;

import jakarta.persistence.EntityManager;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class OrderCreationService {

    private final BookRepository bookRepository;
    private final OrderRepository orderRepository;
    private final IdempotencyKeyRepository idempotencyRepository;
    private final EntityManager entityManager;

    @Value("${bookstore.shipping:0}")
    private BigDecimal shipping;

    @Value("${bookstore.tax-rate:0}")
    private BigDecimal taxRate;

    @Value("${bookstore.discount:0}")
    private BigDecimal configuredDiscount;

    public OrderCreationService(
            BookRepository bookRepository,
            OrderRepository orderRepository,
            IdempotencyKeyRepository idempotencyRepository,
            EntityManager entityManager) {
        this.bookRepository = bookRepository;
        this.orderRepository = orderRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.entityManager = entityManager;
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public OrderResponse createOrder(
            CreateOrderRequest request,
            String idempotencyKey) {

        Set<Long> bookIds = new HashSet<>();

        for (OrderLineRequest line : request.items()) {
            if (!bookIds.add(line.book_id())) {
                throw new OrderException(
                    400,
                    "duplicate_book",
                    "Include each book once and set its quantity."
                );
            }
        }

        Map<Long, Book> books = new LinkedHashMap<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (OrderLineRequest line : request.items()) {
            Book book = bookRepository.findBookById(line.book_id())
                .orElseThrow(() -> new OrderException(
                    404,
                    "book_not_found",
                    "Book ID " + line.book_id() + " was not found."
                ));

            books.put(book.getId(), book);

            subtotal = subtotal.add(
                book.getPrice().multiply(
                    BigDecimal.valueOf(line.quantity())
                )
            );
        }

        subtotal = money(subtotal);

        if ("failure".equals(request.payment_scenario())) {
            throw new OrderException(
                402,
                "payment_failed",
                "The payment simulator declined this payment. No order was confirmed."
            );
        }

        BigDecimal tax = money(subtotal.multiply(taxRate));
        BigDecimal gross = money(subtotal.add(shipping).add(tax));
        BigDecimal discount = money(configuredDiscount.min(gross));
        BigDecimal total = money(gross.subtract(discount));

        Order order = new Order();
        order.setId(UUID.randomUUID().toString());
        order.setStatus("CONFIRMED");

        CustomerDetailsRequest customer = request.customer();

        order.setCustomerName(customer.name().trim());
        order.setEmail(customer.email().trim());
        order.setPhone(
            customer.phone() == null || customer.phone().isBlank()
                ? null : customer.phone().trim()
        );
        order.setAddressLine(customer.address_line().trim());
        order.setCity(customer.city().trim());
        order.setState(customer.state().trim());
        order.setPostalCode(customer.postal_code().trim());
        order.setCountry(customer.country().trim());

        order.setSubtotal(subtotal);
        order.setShipping(money(shipping));
        order.setTax(tax);
        order.setDiscount(discount);
        order.setTotal(total);

        for (OrderLineRequest line : request.items()) {
            Book book = books.get(line.book_id());

            int updated = bookRepository.deductStock(
                book.getId(),
                line.quantity()
            );

            if (updated != 1) {
                entityManager.refresh(book);

                int available = book.getStock();

                throw new OrderException(
                    409,
                    "insufficient_stock",
                    "Only " + available + " " +
                    (available == 1 ? "copy" : "copies") +
                    " of '" + book.getTitle() + "' remain.",
                    Map.of(
                        "book_id", book.getId(),
                        "available", available
                    )
                );
            }

            OrderItem item = new OrderItem();
            item.setBook(book);
            item.setTitle(book.getTitle());
            item.setQuantity(line.quantity());
            item.setUnitPrice(book.getPrice());
            item.setLineTotal(
                money(book.getPrice().multiply(
                    BigDecimal.valueOf(line.quantity())
                ))
            );

            order.addItem(item);
        }

        orderRepository.saveAndFlush(order);

        idempotencyRepository.saveAndFlush(
            new IdempotencyKey(idempotencyKey, order)
        );

        return toResponse(order);
    }

    private OrderResponse toResponse(Order order) {
        List<OrderResponse.OrderItemResponse> items =
            order.getItems().stream()
                .map(item -> new OrderResponse.OrderItemResponse(
                    item.getBook().getId(),
                    item.getTitle(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.getLineTotal()
                ))
                .toList();

        return new OrderResponse(
            order.getId(),
            order.getStatus(),
            order.getSubtotal(),
            order.getShipping(),
            order.getTax(),
            order.getDiscount(),
            order.getTotal(),
            items
        );
    }
}