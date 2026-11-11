package com.bookstore.books;

import com.bookstore.orders.IdempotencyKeyRepository;
import com.bookstore.orders.InventoryMovementRepository;
import com.bookstore.orders.OrderItemRepository;
import com.bookstore.orders.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Objects;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:orders;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class OrderRetrievalIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    @BeforeEach
    void clearDatabase() {
        idempotencyKeyRepository.deleteAll();
        inventoryMovementRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        bookRepository.deleteAll();
    }

    @Test
    void customerSessionCanRetrieveOrderDetailsAndHistoryButNotOtherSessionsOrders()
            throws Exception {
        Book book = saveBook();
        Book secondBook = saveBook("Second session-owned book", new BigDecimal("7.25"));
        MockHttpSession ownerSession = new MockHttpSession();
        MockHttpSession otherSession = new MockHttpSession();

        var createResponse = mockMvc.perform(post("/api/orders")
                .session(ownerSession)
                .header("Idempotency-Key", "session-order")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(Objects.requireNonNull(checkoutRequest(requireBookId(book), requireBookId(secondBook)))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.subtotal").value(32.25))
            .andExpect(jsonPath("$.total").value(32.25))
            .andExpect(jsonPath("$.items[0].quantity").value(2))
            .andExpect(jsonPath("$.items[0].unit_price").value(12.5))
            .andExpect(jsonPath("$.items[0].line_total").value(25.0))
            .andExpect(jsonPath("$.items[1].quantity").value(1))
            .andExpect(jsonPath("$.items[1].unit_price").value(7.25))
            .andExpect(jsonPath("$.items[1].line_total").value(7.25))
            .andReturn();
        String orderId = objectMapper.readTree(
            createResponse.getResponse().getContentAsString()
        ).get("order_id").asText();

        mockMvc.perform(get("/api/orders/{orderId}", orderId)
                .session(ownerSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.order_id").value(orderId))
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.items[0].title").value("Session-owned book"));

        mockMvc.perform(get("/api/orders").session(ownerSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].order_id").value(orderId));

        mockMvc.perform(get("/api/orders/{orderId}", orderId)
                .session(otherSession))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail.code").value("order_not_found"));

        mockMvc.perform(get("/api/orders").session(otherSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post("/api/orders/{orderId}/cancel", orderId)
                .session(otherSession))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail.code").value("order_not_found"));
    }

    @Test
    void idempotencyReplayDoesNotExposeAnotherSessionOrder() throws Exception {
        Book book = saveBook();
        MockHttpSession ownerSession = new MockHttpSession();
        MockHttpSession otherSession = new MockHttpSession();

        mockMvc.perform(post("/api/orders")
                .session(ownerSession)
                .header("Idempotency-Key", "private-session-key")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(Objects.requireNonNull(checkoutRequest(requireBookId(book)))))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/orders")
                .session(otherSession)
                .header("Idempotency-Key", "private-session-key")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(Objects.requireNonNull(checkoutRequest(requireBookId(book)))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail.code").value("idempotency_key_conflict"));
    }

    private Book saveBook() {
        return saveBook("Session-owned book", new BigDecimal("12.50"));
    }

    private Book saveBook(String title, BigDecimal price) {
        Book book = new Book();
        book.setTitle(title);
        book.setAuthor("Test Author");
        book.setCategory("Test");
        book.setDescription("A book used to test order retrieval.");
        book.setPrice(price);
        book.setStock(5);
        return bookRepository.saveAndFlush(book);
    }

    private Long requireBookId(Book book) {
        return Objects.requireNonNull(book.getId(), "Book ID must be set");
    }

    private String checkoutRequest(Long bookId) {
        return checkoutRequest(bookId, null);
    }

    private String checkoutRequest(Long bookId, Long secondBookId) {
        String secondLine = secondBookId == null
            ? ""
            : ", {\"book_id\": %d, \"quantity\": 1}".formatted(secondBookId);
        return """
            {
              "items": [{"book_id": %d, "quantity": 2}%s],
              "customer": {
                "name": "Session Customer",
                "email": "same@example.com",
                "address_line": "1 Test Street",
                "city": "Test City",
                "state": "Test State",
                "postal_code": "12345",
                "country": "Test Country"
              },
              "payment_scenario": "success"
            }
            """.formatted(bookId, secondLine);
    }
}
