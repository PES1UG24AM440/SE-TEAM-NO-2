package com.bookstore.books;

import com.bookstore.orders.IdempotencyKeyRepository;
import com.bookstore.orders.InventoryMovement;
import com.bookstore.orders.InventoryMovementRepository;
import com.bookstore.orders.InventoryMovementType;
import com.bookstore.orders.OrderException;
import com.bookstore.orders.OrderItemRepository;
import com.bookstore.orders.OrderRepository;
import com.bookstore.orders.OrderService;
import com.bookstore.orders.dto.CreateOrderRequest;
import com.bookstore.orders.dto.CustomerDetailsRequest;
import com.bookstore.orders.dto.OrderLineRequest;
import com.bookstore.orders.dto.OrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:orders;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class OrderCreationTransactionTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private InventoryMovementRepository inventoryMovementRepository;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void clearDatabase() {
        idempotencyKeyRepository.deleteAll();
        inventoryMovementRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        bookRepository.deleteAll();
    }

    @Test
    void successfulCheckoutDeductsStockOnceAndPersistsOrder() {
        Book book = saveBook("Atomic transactions", 5);

        OrderResponse response = orderService.createOrder(
            request(List.of(line(book, 2)), "success"),
            "successful-checkout"
        );

        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(stockOf(book)).isEqualTo(3);
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(orderItemRepository.count()).isEqualTo(1);
        assertThat(idempotencyKeyRepository.count()).isEqualTo(1);
        assertThat(movementsFor(response.order_id()))
            .singleElement()
            .satisfies(movement -> {
                assertThat(movement.getQuantityChange()).isEqualTo(-2);
                assertThat(movement.getMovementType())
                    .isEqualTo(InventoryMovementType.ORDER_DEDUCTION);
                assertThat(movement.getCreatedAt()).isNotNull();
            });
    }

    @Test
    void failedPaymentDoesNotPersistOrderOrDeductStock() {
        Book book = saveBook("Payment failure", 5);

        assertThatThrownBy(() -> orderService.createOrder(
            request(List.of(line(book, 2)), "failure"),
            "failed-payment"
        ))
            .isInstanceOfSatisfying(OrderException.class, exception ->
                assertThat(exception.getCode()).isEqualTo("payment_failed"));

        assertThat(stockOf(book)).isEqualTo(5);
        assertNoOrderRecords();
    }

    @Test
    void insufficientStockRollsBackEarlierDeductionsAndOrderRecords() {
        Book firstBook = saveBook("Available book", 5);
        Book secondBook = saveBook("Low-stock book", 1);

        assertThatThrownBy(() -> orderService.createOrder(
            request(List.of(line(firstBook, 2), line(secondBook, 2)), "success"),
            "insufficient-stock"
        ))
            .isInstanceOfSatisfying(OrderException.class, exception ->
                assertThat(exception.getCode()).isEqualTo("insufficient_stock"));

        assertThat(stockOf(firstBook)).isEqualTo(5);
        assertThat(stockOf(secondBook)).isEqualTo(1);
        assertNoOrderRecords();
    }

    @Test
    void repeatedRequestWithSameIdempotencyKeyDoesNotDeductStockTwice() {
        Book book = saveBook("Idempotent checkout", 5);
        CreateOrderRequest checkout = request(List.of(line(book, 2)), "success");

        OrderResponse firstResponse =
            orderService.createOrder(checkout, "same-checkout-key");
        OrderResponse retryResponse =
            orderService.createOrder(checkout, "same-checkout-key");

        assertThat(retryResponse.order_id()).isEqualTo(firstResponse.order_id());
        assertThat(stockOf(book)).isEqualTo(3);
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(orderItemRepository.count()).isEqualTo(1);
        assertThat(idempotencyKeyRepository.count()).isEqualTo(1);
        assertThat(movementsFor(firstResponse.order_id()))
            .singleElement()
            .satisfies(movement ->
                assertThat(movement.getQuantityChange()).isEqualTo(-2));
    }

    @Test
    void cancellingConfirmedOrderRestoresStockOnceAndRejectsRepeatCancellation()
            throws Exception {
        Book book = saveBook("Cancellation", 5);
        OrderResponse order = orderService.createOrder(
            request(List.of(line(book, 2)), "success"),
            "cancel-checkout"
        );
        assertThat(stockOf(book)).isEqualTo(3);

        mockMvc.perform(post("/api/orders/{orderId}/cancel", order.order_id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(stockOf(book)).isEqualTo(5);
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(orderItemRepository.count()).isEqualTo(1);
        assertThat(movementsFor(order.order_id())).hasSize(2);
        assertThat(movementsFor(order.order_id()))
            .extracting(movement -> movement.getMovementType())
            .containsExactlyInAnyOrder(
                InventoryMovementType.ORDER_DEDUCTION,
                InventoryMovementType.ORDER_CANCELLATION_RESTOCK
            );
        assertThat(movementsFor(order.order_id()))
            .extracting(movement -> movement.getQuantityChange())
            .containsExactlyInAnyOrder(-2, 2);

        mockMvc.perform(post("/api/orders/{orderId}/cancel", order.order_id()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail.code").value("invalid_status_transition"));

        assertThat(stockOf(book)).isEqualTo(5);
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(orderItemRepository.count()).isEqualTo(1);
        assertThat(movementsFor(order.order_id())).hasSize(2);
    }

    @Test
    void cancellingUnknownOrderReturnsNotFound() throws Exception {
        mockMvc.perform(post(
                "/api/orders/{orderId}/cancel",
                "missing-order"
            ))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail.code").value("order_not_found"));
    }

    @Test
    void concurrentCancellationRestoresStockOnlyOnce() throws Exception {
        Book book = saveBook("Concurrent cancellation", 5);
        OrderResponse order = orderService.createOrder(
            request(List.of(line(book, 2)), "success"),
            "concurrent-cancel-checkout"
        );
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            var first = executor.submit(() -> {
                start.await();
                return mockMvc.perform(
                    post("/api/orders/{orderId}/cancel", order.order_id())
                ).andReturn();
            });
            var second = executor.submit(() -> {
                start.await();
                return mockMvc.perform(
                    post("/api/orders/{orderId}/cancel", order.order_id())
                ).andReturn();
            });
            start.countDown();

            List<Integer> responseStatuses = List.of(
                first.get(10, TimeUnit.SECONDS).getResponse().getStatus(),
                second.get(10, TimeUnit.SECONDS).getResponse().getStatus()
            );

            assertThat(responseStatuses).containsExactlyInAnyOrder(200, 409);
            assertThat(stockOf(book)).isEqualTo(5);
            assertThat(orderRepository.count()).isEqualTo(1);
            assertThat(movementsFor(order.order_id())).hasSize(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void databaseRejectsDuplicateMovementForSameOrderBookAndType() {
        Book book = saveBook("Unique ledger row", 5);
        OrderResponse orderResponse = orderService.createOrder(
            request(List.of(line(book, 2)), "success"),
            "unique-ledger-checkout"
        );
        var order = orderRepository.findById(
            Objects.requireNonNull(orderResponse.order_id())
        ).orElseThrow();

        assertThatThrownBy(() -> inventoryMovementRepository.saveAndFlush(
            new InventoryMovement(
                book,
                order,
                -2,
                InventoryMovementType.ORDER_DEDUCTION
            )
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(movementsFor(orderResponse.order_id())).hasSize(1);
        assertThat(stockOf(book)).isEqualTo(3);
    }

    @Test
    void duplicateRestockLedgerEntryRollsBackCancellationAndStockUpdate() {
        Book book = saveBook("Atomic ledger rollback", 5);
        OrderResponse orderResponse = orderService.createOrder(
            request(List.of(line(book, 2)), "success"),
            "atomic-ledger-checkout"
        );
        var order = orderRepository.findById(
            Objects.requireNonNull(orderResponse.order_id())
        ).orElseThrow();
        inventoryMovementRepository.saveAndFlush(new InventoryMovement(
            book,
            order,
            2,
            InventoryMovementType.ORDER_CANCELLATION_RESTOCK
        ));

        assertThatThrownBy(() -> orderService.cancelOrder(orderResponse.order_id()))
            .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(stockOf(book)).isEqualTo(3);
        assertThat(orderRepository.findById(
            Objects.requireNonNull(orderResponse.order_id())
        ).orElseThrow()
            .getStatus().name()).isEqualTo("CONFIRMED");
        assertThat(movementsFor(orderResponse.order_id())).hasSize(2);
    }

    private Book saveBook(String title, int stock) {
        Book book = new Book();
        book.setTitle(title);
        book.setAuthor("Regression Test");
        book.setCategory("Testing");
        book.setPrice(new BigDecimal("10.00"));
        book.setStock(stock);
        return bookRepository.saveAndFlush(book);
    }

    private int stockOf(Book book) {
        Long bookId = Objects.requireNonNull(book.getId(), "Book id must not be null");
        return bookRepository.findById(bookId)
            .orElseThrow()
            .getStock();
    }

    private List<InventoryMovement> movementsFor(String orderId) {
        return inventoryMovementRepository.findByOrder_Id(orderId);
    }

    private void assertNoOrderRecords() {
        assertThat(orderRepository.count()).isZero();
        assertThat(orderItemRepository.count()).isZero();
        assertThat(idempotencyKeyRepository.count()).isZero();
        assertThat(inventoryMovementRepository.count()).isZero();
    }

    private OrderLineRequest line(Book book, int quantity) {
        return new OrderLineRequest(book.getId(), quantity);
    }

    private CreateOrderRequest request(
            List<OrderLineRequest> items,
            String paymentScenario) {
        return new CreateOrderRequest(
            items,
            new CustomerDetailsRequest(
                "Test Customer",
                "customer@example.com",
                null,
                "1 Test Street",
                "Test City",
                "Test State",
                "12345",
                "Test Country"
            ),
            paymentScenario
        );
    }
}
