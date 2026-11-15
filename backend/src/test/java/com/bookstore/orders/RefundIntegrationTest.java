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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #71 with the default policy (full refund). Shipping 40, tax 5% and a 10.00 discount are
 * configured so the test proves all of them come from the stored order: 2 x 100 = 200 subtotal,
 * + 40 shipping + 10 tax - 10 discount = 240 paid, 240 refunded.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:refundtest;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "bookstore.shipping=40",
    "bookstore.tax-rate=0.05",
    "bookstore.discount=10"
})
@AutoConfigureMockMvc
class RefundIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
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
    void cancellingAnEligibleOrderStoresAFullRefundFromTheStoredTotals() throws Exception {
        Book book = saveBook(5);
        MockHttpSession session = new MockHttpSession();
        String orderId = placeOrder(session, book, "full-refund");

        // Today's price changes after checkout; the refund must still use what was paid.
        listings.update(book.getId(), new ListingRequest(
            book.getTitle(), "Test Author", "Testing", null, new BigDecimal("999.00"), 3));

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId).session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.total").value(240.00))
            .andExpect(jsonPath("$.refund.amount").value(240.00))
            .andExpect(jsonPath("$.refund.type").value("FULL"))
            .andExpect(jsonPath("$.refund.status").value("REFUNDED"))
            .andExpect(jsonPath("$.refund.order_total").value(240.00))
            .andExpect(jsonPath("$.refund.shipping_withheld").value(0.00))
            .andExpect(jsonPath("$.refund.cancellation_fee").value(0.00));

        Refund stored = refunds.findByOrder_Id(orderId).orElseThrow();
        assertThat(stored.getAmount()).isEqualByComparingTo("240.00");

        mockMvc.perform(get("/api/orders/{id}", orderId).session(session))
            .andExpect(jsonPath("$.refund.amount").value(240.00));
    }

    @Test
    void repeatedCancellationIsRejectedAndNeverCreatesASecondRefund() throws Exception {
        Book book = saveBook(5);
        MockHttpSession session = new MockHttpSession();
        String orderId = placeOrder(session, book, "duplicate-refund");

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId).session(session))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/{id}/cancel", orderId).session(session))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail.code").value("invalid_status_transition"));

        assertThat(refunds.count()).isEqualTo(1);
        assertThat(books.findById(book.getId()).orElseThrow().getStock()).isEqualTo(5);
    }

    @Test
    void confirmedOrdersHaveNoRefundAndAnotherSessionCannotCancel() throws Exception {
        Book book = saveBook(5);
        MockHttpSession owner = new MockHttpSession();
        String orderId = placeOrder(owner, book, "no-refund-yet");

        mockMvc.perform(get("/api/orders/{id}", orderId).session(owner))
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.refund").doesNotExist())
            .andExpect(jsonPath("$.created_at").isNotEmpty());

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId).session(new MockHttpSession()))
            .andExpect(status().isNotFound());
        assertThat(refunds.count()).isZero();
    }

    private Book saveBook(int stock) {
        return listings.create(null, new ListingRequest(
            "Refundable", "Test Author", "Testing", null, new BigDecimal("100.00"), stock));
    }

    private String placeOrder(MockHttpSession session, Book book, String key) throws Exception {
        String body = mockMvc.perform(post("/api/orders").session(session)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(TestAccounts.checkoutJson(book.getId(), 2)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.total").value(240.00))
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("order_id").asText();
    }
}
