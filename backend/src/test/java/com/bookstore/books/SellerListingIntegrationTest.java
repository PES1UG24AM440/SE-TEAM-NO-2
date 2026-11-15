package com.bookstore.books;

import com.bookstore.orders.IdempotencyKeyRepository;
import com.bookstore.orders.InventoryMovementRepository;
import com.bookstore.orders.OrderItemRepository;
import com.bookstore.orders.OrderRepository;
import com.bookstore.support.TestAccounts;
import com.bookstore.users.AppUser;
import com.bookstore.users.Role;
import com.bookstore.users.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
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

import java.math.BigDecimal;

import static com.bookstore.support.TestAccounts.checkoutJson;
import static com.bookstore.support.TestAccounts.listingJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Issue #72 acceptance tests (TC-SELL-01, TC-SELL-02). */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:sellertest;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class SellerListingIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private BookRepository books;
    @Autowired private OrderRepository orders;
    @Autowired private OrderItemRepository orderItems;
    @Autowired private InventoryMovementRepository movements;
    @Autowired private IdempotencyKeyRepository idempotencyKeys;

    private TestAccounts accounts;
    private AppUser alice;
    private MockHttpSession aliceSession;
    private MockHttpSession bobSession;

    @BeforeEach
    void setUp() throws Exception {
        clean();
        accounts = new TestAccounts(mockMvc, users, passwordEncoder);
        alice = accounts.create("alice@sellers.test", Role.SELLER);
        accounts.create("bob@sellers.test", Role.SELLER);
        aliceSession = accounts.login("alice@sellers.test");
        bobSession = accounts.login("bob@sellers.test");
    }

    @AfterEach
    void clean() {
        idempotencyKeys.deleteAll();
        movements.deleteAll();
        orderItems.deleteAll();
        orders.deleteAll();
        books.deleteAll();
        users.deleteAll();
    }

    @Test
    void sellerCreatesListingThatPersistsAndAppearsInSearch() throws Exception {
        long id = createListing(aliceSession, "Seller Search Book", "199.50", 4);

        Book saved = books.findById(id).orElseThrow();
        assertThat(saved.getSellerId()).isEqualTo(alice.getId());
        assertThat(saved.isListed()).isTrue();

        mockMvc.perform(get("/api/books").param("q", "Seller Search"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(id))
            .andExpect(jsonPath("$[0].price").value(199.50));

        mockMvc.perform(get("/api/seller/books").session(aliceSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/seller/books").session(bobSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void sellerEditsOwnPriceAndStock() throws Exception {
        long id = createListing(aliceSession, "Editable", "100.00", 2);

        mockMvc.perform(put("/api/seller/books/{id}", id).session(aliceSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(listingJson("Editable, 2nd ed.", "120.00", 9)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Editable, 2nd ed."))
            .andExpect(jsonPath("$.stock").value(9));

        Book saved = books.findById(id).orElseThrow();
        assertThat(saved.getPrice()).isEqualByComparingTo(new BigDecimal("120.00"));
        assertThat(saved.getStock()).isEqualTo(9);
    }

    @Test
    void sellerCannotChangeAnotherSellersListing() throws Exception {
        long id = createListing(aliceSession, "Alice Only", "100.00", 2);

        mockMvc.perform(put("/api/seller/books/{id}", id).session(bobSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(listingJson("Hijacked", "1.00", 0)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail.code").value("not_listing_owner"));

        mockMvc.perform(delete("/api/seller/books/{id}", id).session(bobSession))
            .andExpect(status().isForbidden());

        Book unchanged = books.findById(id).orElseThrow();
        assertThat(unchanged.getTitle()).isEqualTo("Alice Only");
        assertThat(unchanged.isListed()).isTrue();
    }

    @Test
    void sellerCannotEditStoreOwnedSeedBooks() throws Exception {
        Book seed = new Book();
        seed.setTitle("Store Book");
        seed.setAuthor("Someone");
        seed.setCategory("Fiction");
        seed.setPrice(new BigDecimal("50.00"));
        seed.setStock(3);
        long id = books.saveAndFlush(seed).getId();

        mockMvc.perform(put("/api/seller/books/{id}", id).session(aliceSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(listingJson("Mine now", "1.00", 1)))
            .andExpect(status().isForbidden());
    }

    @Test
    void invalidListingFieldsAreRejectedOnTheServer() throws Exception {
        mockMvc.perform(post("/api/seller/books").session(aliceSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title": "  ", "author": "", "category": "", "price": -5, "stock": -1}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail.code").value("validation_error"))
            .andExpect(jsonPath("$.detail.fields[*].field").value(hasItem("title")))
            .andExpect(jsonPath("$.detail.fields[*].field").value(hasItem("author")))
            .andExpect(jsonPath("$.detail.fields[*].field").value(hasItem("category")))
            .andExpect(jsonPath("$.detail.fields[*].field").value(hasItem("price")))
            .andExpect(jsonPath("$.detail.fields[*].field").value(hasItem("stock")));

        mockMvc.perform(post("/api/seller/books").session(aliceSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(listingJson("Bad price", "10.999", 1)))
            .andExpect(status().isBadRequest());

        assertThat(books.count()).isZero();
    }

    @Test
    void deactivatedListingDisappearsFromCatalogueAndCannotBeOrderedUntilReactivated() throws Exception {
        long id = createListing(aliceSession, "Hide Me", "80.00", 5);

        mockMvc.perform(delete("/api/seller/books/{id}", id).session(aliceSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/books"))
            .andExpect(jsonPath("$[*].id").value(not(hasItem((int) id))));
        mockMvc.perform(get("/api/books/{id}", id))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/orders")
                .header("Idempotency-Key", "inactive-listing-order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(checkoutJson(id, 1)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail.code").value("book_unavailable"));
        assertThat(books.findById(id).orElseThrow().getStock()).isEqualTo(5);

        mockMvc.perform(get("/api/seller/books").session(aliceSession))
            .andExpect(jsonPath("$[0].active").value(false));

        mockMvc.perform(post("/api/seller/books/{id}/activate", id).session(aliceSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(true));
        mockMvc.perform(get("/api/books/{id}", id))
            .andExpect(status().isOk());
    }

    @Test
    void deactivatedSellerAccountIsBlockedEvenWithAnOpenSession() throws Exception {
        alice.setActive(false);
        users.saveAndFlush(alice);

        mockMvc.perform(get("/api/seller/books").session(aliceSession))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail.code").value("account_disabled"));
    }

    @Test
    void customersAndGuestsCannotUseSellerEndpoints() throws Exception {
        accounts.create("carol@customers.test", Role.CUSTOMER);
        MockHttpSession customer = accounts.login("carol@customers.test");

        mockMvc.perform(post("/api/seller/books").session(customer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(listingJson("Nope", "10.00", 1)))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/seller/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content(listingJson("Nope", "10.00", 1)))
            .andExpect(status().isUnauthorized());
        assertThat(books.count()).isZero();
    }

    private long createListing(MockHttpSession session, String title, String price, int stock) throws Exception {
        String body = mockMvc.perform(post("/api/seller/books").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(listingJson(title, price, stock)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.get("id").asLong();
    }
}
