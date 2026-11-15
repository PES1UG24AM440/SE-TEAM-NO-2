package com.bookstore.orders;

import com.bookstore.books.Book;
import com.bookstore.books.BookRepository;
import com.bookstore.books.ListingRequest;
import com.bookstore.books.ListingService;
import com.bookstore.support.TestAccounts;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #71 with a stricter (example) policy: shipping not refunded, 10% cancellation fee,
 * 1-hour cancellation window. Order: 200 + 40 shipping + 10 tax - 10 discount = 240 paid.
 * Refund: 240 - 40 shipping = 200 refundable, - 20 fee = 180.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:partialrefundtest;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "bookstore.shipping=40",
    "bookstore.tax-rate=0.05",
    "bookstore.discount=10",
    "bookstore.refund.refund-shipping=false",
    "bookstore.refund.cancellation-fee-rate=0.10",
    "bookstore.refund.cancellation-window-hours=1"
})
@AutoConfigureMockMvc
class PartialRefundPolicyIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private BookRepository books;
    @Autowired private ListingService listings;
    @Autowired private OrderRepository orders;
    @Autowired private OrderItemRepository orderItems;
    @Autowired private InventoryMovementRepository movements;
    @Autowired private IdempotencyKeyRepository idempotencyKeys;
    @Autowired private RefundRepository refunds;

    @BeforeEach
    @AfterEach
    void clean() {
        refunds.deleteAll();
        idempotencyKeys.deleteAll();
        movements.deleteAll();
        orderItems.deleteAll();
        orders.deleteAll();
        books.deleteAll();
    }

    @Test
    void partialRefundWithholdsShippingAndTheCancellationFee() throws Exception {
        Book book = saveBook();
        MockHttpSession session = new MockHttpSession();
        String orderId = placeOrder(session, book, "partial-refund");

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId).session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.refund.type").value("PARTIAL"))
            .andExpect(jsonPath("$.refund.order_total").value(240.00))
            .andExpect(jsonPath("$.refund.shipping_withheld").value(40.00))
            .andExpect(jsonPath("$.refund.cancellation_fee").value(20.00))
            .andExpect(jsonPath("$.refund.amount").value(180.00));

        assertThat(refunds.findByOrder_Id(orderId).orElseThrow().getAmount()).isEqualByComparingTo("180.00");
    }

    @Test
    void ordersOutsideTheCancellationWindowAreIneligibleAndGetNoRefund() throws Exception {
        Book book = saveBook();
        MockHttpSession session = new MockHttpSession();
        String orderId = placeOrder(session, book, "too-late");
        jdbc.update("update orders set created_at = ? where id = ?",
            Timestamp.from(Instant.now().minus(Duration.ofHours(2))), orderId);

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId).session(session))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail.code").value("cancellation_not_eligible"));

        mockMvc.perform(get("/api/orders/{id}", orderId).session(session))
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.refund").doesNotExist());
        assertThat(refunds.count()).isZero();
        assertThat(books.findById(book.getId()).orElseThrow().getStock()).isEqualTo(3);
    }

    private Book saveBook() {
        return listings.create(null, new ListingRequest(
            "Policy Book", "Test Author", "Testing", null, new BigDecimal("100.00"), 5));
    }

    private String placeOrder(MockHttpSession session, Book book, String key) throws Exception {
        String body = mockMvc.perform(post("/api/orders").session(session)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(TestAccounts.checkoutJson(book.getId(), 2)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("order_id").asText();
    }
}
