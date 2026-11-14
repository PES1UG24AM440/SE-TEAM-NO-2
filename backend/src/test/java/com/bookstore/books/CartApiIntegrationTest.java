package com.bookstore.books;

import com.bookstore.orders.OrderItemRepository;
import com.bookstore.orders.OrderRepository;
import com.bookstore.orders.IdempotencyKeyRepository;
import com.bookstore.orders.InventoryMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:orders;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class CartApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
    void updatesQuantityAndCalculatesSubtotalFromCurrentBookPrice() throws Exception {
        Book book = saveBook("Cart book", "12.50", 4);
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(put("/api/cart/items/{bookId}", book.getId())
                .session(session)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"quantity\":2}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].book_id").value(book.getId()))
            .andExpect(jsonPath("$.items[0].quantity").value(2))
            .andExpect(jsonPath("$.items[0].unit_price").value(12.5))
            .andExpect(jsonPath("$.items[0].line_total").value(25.0))
            .andExpect(jsonPath("$.subtotal").value(25.0));

        book.setPrice(new BigDecimal("15.00"));
        bookRepository.saveAndFlush(book);
        mockMvc.perform(get("/api/cart").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].unit_price").value(15.0))
            .andExpect(jsonPath("$.items[0].line_total").value(30.0))
            .andExpect(jsonPath("$.subtotal").value(30.0));
    }

    @Test
    void rejectsInvalidQuantitiesUnknownBooksAndStockOverages() throws Exception {
        Book book = saveBook("Limited book", "8.00", 2);
        MockHttpSession session = new MockHttpSession();

        for (int quantity : new int[] {0, -1}) {
            mockMvc.perform(put("/api/cart/items/{bookId}", book.getId())
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content("{\"quantity\":" + quantity + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail.code").value("validation_error"));
        }

        mockMvc.perform(put("/api/cart/items/{bookId}", 999L)
                .session(session)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"quantity\":1}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail.code").value("book_not_found"));

        mockMvc.perform(put("/api/cart/items/{bookId}", book.getId())
                .session(session)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"quantity\":3}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail.code").value("insufficient_stock"));

        mockMvc.perform(get("/api/cart").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty())
            .andExpect(jsonPath("$.subtotal").value(0.0));
    }

    @Test
    void removesOneItemAndClearsCartWithinOnlyTheCurrentSession() throws Exception {
        Book first = saveBook("First book", "10.00", 4);
        Book second = saveBook("Second book", "20.00", 4);
        MockHttpSession session = new MockHttpSession();
        MockHttpSession otherSession = new MockHttpSession();

        putItem(session, first, 1);
        putItem(session, second, 2);

        mockMvc.perform(delete("/api/cart/items/{bookId}", first.getId())
                .session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].book_id").value(second.getId()))
            .andExpect(jsonPath("$.subtotal").value(40.0));

        mockMvc.perform(get("/api/cart").session(otherSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty())
            .andExpect(jsonPath("$.subtotal").value(0.0));

        mockMvc.perform(delete("/api/cart").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty())
            .andExpect(jsonPath("$.subtotal").value(0.0));
    }

    private void putItem(MockHttpSession session, Book book, int quantity)
            throws Exception {
        mockMvc.perform(put("/api/cart/items/{bookId}", book.getId())
                .session(session)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"quantity\":" + quantity + "}"))
            .andExpect(status().isOk());
    }

    private Book saveBook(String title, String price, int stock) {
        Book book = new Book();
        book.setTitle(title);
        book.setAuthor("Test Author");
        book.setCategory("Testing");
        book.setPrice(new BigDecimal(price));
        book.setStock(stock);
        return bookRepository.saveAndFlush(book);
    }
}
