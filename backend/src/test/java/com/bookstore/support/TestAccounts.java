package com.bookstore.support;

import com.bookstore.users.AppUser;
import com.bookstore.users.Role;
import com.bookstore.users.UserRepository;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Creates accounts and signs them in through the real /api/auth/login endpoint. */
public class TestAccounts {

    public static final String PASSWORD = "correct-horse-1";

    private final MockMvc mockMvc;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public TestAccounts(MockMvc mockMvc, UserRepository users, PasswordEncoder passwordEncoder) {
        this.mockMvc = mockMvc;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    public AppUser create(String email, Role role) {
        return users.saveAndFlush(new AppUser(email, passwordEncoder.encode(PASSWORD), "Test " + role, role));
    }

    public MockHttpSession login(String email) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isOk());
        return session;
    }

    public static String listingJson(String title, String price, int stock) {
        return """
            {"title": "%s", "author": "Test Author", "category": "Testing",
             "description": "A listing used in tests.", "price": %s, "stock": %d}
            """.formatted(title, price, stock);
    }

    public static String checkoutJson(Long bookId, int quantity) {
        return """
            {
              "items": [{"book_id": %d, "quantity": %d}],
              "customer": {
                "name": "Test Customer", "email": "customer@example.com",
                "address_line": "1 Test Street", "city": "Test City", "state": "Test State",
                "postal_code": "12345", "country": "Test Country"
              },
              "payment_scenario": "success"
            }
            """.formatted(bookId, quantity);
    }
}
