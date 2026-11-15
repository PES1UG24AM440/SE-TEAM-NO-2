package com.bookstore.admin;

import com.bookstore.books.Book;
import com.bookstore.books.BookRepository;
import com.bookstore.books.ListingRequest;
import com.bookstore.books.ListingService;
import com.bookstore.orders.IdempotencyKeyRepository;
import com.bookstore.orders.InventoryMovementRepository;
import com.bookstore.orders.OrderItemRepository;
import com.bookstore.orders.OrderRepository;
import com.bookstore.support.TestAccounts;
import com.bookstore.users.AppUser;
import com.bookstore.users.Role;
import com.bookstore.users.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.List;

import static com.bookstore.support.TestAccounts.checkoutJson;
import static com.bookstore.support.TestAccounts.listingJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Issue #73 acceptance tests (TC-ADMIN-01, TC-ADMIN-02, TC-SEC-02). */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:admintest;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class AdminIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private BookRepository books;
    @Autowired private OrderRepository orders;
    @Autowired private OrderItemRepository orderItems;
    @Autowired private InventoryMovementRepository movements;
    @Autowired private IdempotencyKeyRepository idempotencyKeys;
    @Autowired private AuditLogRepository auditLog;
    @Autowired private ListingService listings;

    private TestAccounts accounts;
    private AppUser admin;
    private AppUser customer;
    private MockHttpSession adminSession;

    @BeforeEach
    void setUp() throws Exception {
        clean();
        accounts = new TestAccounts(mockMvc, users, passwordEncoder);
        admin = accounts.create("admin@admins.test", Role.ADMIN);
        customer = accounts.create("cara@customers.test", Role.CUSTOMER);
        accounts.create("sam@sellers.test", Role.SELLER);
        adminSession = accounts.login("admin@admins.test");
    }

    @AfterEach
    void clean() {
        auditLog.deleteAll();
        idempotencyKeys.deleteAll();
        movements.deleteAll();
        orderItems.deleteAll();
        orders.deleteAll();
        books.deleteAll();
        users.deleteAll();
    }

    @Test
    void everyAdminEndpointRejectsGuestsWith401AndOtherRolesWith403() throws Exception {
        MockHttpSession customerSession = accounts.login("cara@customers.test");
        MockHttpSession sellerSession = accounts.login("sam@sellers.test");
        Book book = saveBook("Guarded", 3);

        List<MockHttpServletRequestBuilder> requests = List.of(
            get("/api/admin/summary"),
            get("/api/admin/users"),
            post("/api/admin/users/{id}/deactivate", customer.getId()),
            get("/api/admin/books"),
            put("/api/admin/books/{id}", book.getId())
                .contentType(MediaType.APPLICATION_JSON).content(listingJson("X", "1.00", 1)),
            post("/api/admin/books/{id}/deactivate", book.getId()),
            get("/api/admin/orders"),
            get("/api/admin/audit-log"));

        for (MockHttpServletRequestBuilder request : requests) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
            mockMvc.perform(request.session(customerSession)).andExpect(status().isForbidden());
            mockMvc.perform(request.session(sellerSession)).andExpect(status().isForbidden());
        }

        assertThat(users.findById(customer.getId()).orElseThrow().isActive()).isTrue();
        assertThat(books.findById(book.getId()).orElseThrow().getTitle()).isEqualTo("Guarded");
        assertThat(auditLog.count()).isZero();
    }

    @Test
    void summaryCountsCatalogueUsersAndOrders() throws Exception {
        Book book = saveBook("Counted", 5);
        Book gone = saveBook("Sold out", 0);
        gone.setActive(false);
        books.saveAndFlush(gone);
        placeOrder(book, 2, "summary-order");

        mockMvc.perform(get("/api/admin/summary").session(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.books.total").value(2))
            .andExpect(jsonPath("$.books.active").value(1))
            .andExpect(jsonPath("$.books.inactive").value(1))
            .andExpect(jsonPath("$.users.total").value(3))
            .andExpect(jsonPath("$.users.customers").value(1))
            .andExpect(jsonPath("$.users.sellers").value(1))
            .andExpect(jsonPath("$.users.admins").value(1))
            .andExpect(jsonPath("$.orders.total").value(1))
            .andExpect(jsonPath("$.orders.confirmed").value(1))
            .andExpect(jsonPath("$.orders.confirmed_revenue").value(200.00));
    }

    @Test
    void adminDeactivatesAndReactivatesAnAccountAndItIsAudited() throws Exception {
        MockHttpSession customerSession = accounts.login("cara@customers.test");

        mockMvc.perform(get("/api/admin/users").session(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].password_hash").doesNotExist());

        mockMvc.perform(post("/api/admin/users/{id}/deactivate", customer.getId()).session(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));
        assertThat(users.findById(customer.getId()).orElseThrow().isActive()).isFalse();

        // The deactivated account can no longer sign in or use its open session.
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"cara@customers.test\",\"password\":\"" + TestAccounts.PASSWORD + "\"}"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").session(customerSession))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail.code").value("account_disabled"));

        mockMvc.perform(post("/api/admin/users/{id}/activate", customer.getId()).session(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(get("/api/admin/audit-log").session(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].action").value("USER_ACTIVATED"))
            .andExpect(jsonPath("$[1].action").value("USER_DEACTIVATED"))
            .andExpect(jsonPath("$[1].actor_email").value("admin@admins.test"))
            .andExpect(jsonPath("$[1].actor_user_id").value(admin.getId()))
            .andExpect(jsonPath("$[1].target_type").value("USER"))
            .andExpect(jsonPath("$[1].target_id").value(String.valueOf(customer.getId())))
            .andExpect(jsonPath("$[1].created_at").isNotEmpty());
    }

    @Test
    void adminCannotDeactivateTheirOwnAccount() throws Exception {
        mockMvc.perform(post("/api/admin/users/{id}/deactivate", admin.getId()).session(adminSession))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail.code").value("cannot_change_own_account"));
        assertThat(users.findById(admin.getId()).orElseThrow().isActive()).isTrue();
        assertThat(auditLog.count()).isZero();
    }

    @Test
    void adminManagesAnyListingAndChangesAreAudited() throws Exception {
        Book book = saveBook("Admin Managed", 4);

        mockMvc.perform(put("/api/admin/books/{id}", book.getId()).session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(listingJson("Admin Managed (corrected)", "75.00", 6)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Admin Managed (corrected)"));

        mockMvc.perform(post("/api/admin/books/{id}/deactivate", book.getId()).session(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(get("/api/books/{id}", book.getId())).andExpect(status().isNotFound());

        mockMvc.perform(get("/api/admin/books").session(adminSession))
            .andExpect(jsonPath("$[0].active").value(false));

        Book saved = books.findById(book.getId()).orElseThrow();
        assertThat(saved.getPrice()).isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(saved.getStock()).isEqualTo(6);
        assertThat(auditLog.findTop200ByOrderByIdDesc())
            .extracting(AuditLog::getAction)
            .containsExactly("LISTING_DEACTIVATED", "LISTING_UPDATED");
    }

    @Test
    void invalidAdminListingEditIsRejectedAndNotAudited() throws Exception {
        Book book = saveBook("Stays", 4);
        mockMvc.perform(put("/api/admin/books/{id}", book.getId()).session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(listingJson("", "-1", -1)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/books/{id}", 999999).session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(listingJson("Missing", "1.00", 1)))
            .andExpect(status().isNotFound());
        assertThat(auditLog.count()).isZero();
    }

    @Test
    void adminInspectsOrdersFromEverySession() throws Exception {
        Book book = saveBook("Ordered", 5);
        String orderId = placeOrder(book, 1, "inspect-order");

        mockMvc.perform(get("/api/admin/orders").session(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].customer_name").value("Test Customer"))
            .andExpect(jsonPath("$[0].order.order_id").value(orderId))
            .andExpect(jsonPath("$[0].order.items[0].title").value("Ordered"));

        mockMvc.perform(get("/api/admin/orders/{id}", orderId).session(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("customer@example.com"))
            .andExpect(jsonPath("$.order.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/admin/orders/{id}", "missing").session(adminSession))
            .andExpect(status().isNotFound());
    }

    private Book saveBook(String title, int stock) {
        // A store-owned listing (no seller), created through the same service the app uses.
        return listings.create(null, new ListingRequest(
            title, "Test Author", "Testing", null, new BigDecimal("100.00"), stock));
    }

    private String placeOrder(Book book, int quantity, String key) throws Exception {
        String body = mockMvc.perform(post("/api/orders")
                .session(new MockHttpSession())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(checkoutJson(book.getId(), quantity)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("order_id").asText();
    }

}
