package com.bookstore.users;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:authtest;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class AuthIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clearUsers() {
        users.deleteAll();
    }

    @Test
    void registerStoresHashedPasswordAndNeverReturnsIt() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("Reader@Example.com", "SELLER")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("reader@example.com"))
            .andExpect(jsonPath("$.role").value("SELLER"))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.password_hash").doesNotExist());

        AppUser saved = users.findByEmailIgnoreCase("reader@example.com").orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo("correct-horse-1");
        assertThat(passwordEncoder.matches("correct-horse-1", saved.getPasswordHash())).isTrue();
    }

    @Test
    void cannotSelfRegisterAsAdminOrReuseAnEmail() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("boss@example.com", "ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail.code").value("invalid_role"));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("dup@example.com", null)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("CUSTOMER"));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("DUP@example.com", null)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail.code").value("email_taken"));
    }

    @Test
    void loginStartsSessionAndMeReturnsTheRole() throws Exception {
        saveUser("seller@example.com", Role.SELLER, true);
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.detail.code").value("unauthenticated"));

        mockMvc.perform(post("/api/auth/login").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("seller@example.com", "correct-horse-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("SELLER"));

        mockMvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("seller@example.com"));

        mockMvc.perform(post("/api/auth/logout").session(session))
            .andExpect(status().isNoContent());
    }

    @Test
    void wrongPasswordUnknownEmailAndDisabledAccountGetTheSameSafeError() throws Exception {
        saveUser("ok@example.com", Role.CUSTOMER, true);
        saveUser("off@example.com", Role.CUSTOMER, false);

        for (String[] attempt : new String[][] {
                {"ok@example.com", "wrong-password"},
                {"nobody@example.com", "correct-horse-1"},
                {"off@example.com", "correct-horse-1"}}) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginJson(attempt[0], attempt[1])))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail.code").value("invalid_credentials"))
                .andExpect(jsonPath("$.detail.message").value("Email or password is incorrect."));
        }
    }

    @Test
    void protectedAreasReturn401WhenAnonymousAnd403ForTheWrongRole() throws Exception {
        saveUser("customer@example.com", Role.CUSTOMER, true);
        MockHttpSession customer = login("customer@example.com");

        mockMvc.perform(get("/api/admin/users"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/seller/books"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/users").session(customer))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail.code").value("forbidden"));
        mockMvc.perform(get("/api/seller/books").session(customer))
            .andExpect(status().isForbidden());
    }

    @Test
    void guestCatalogueStaysPublic() throws Exception {
        mockMvc.perform(get("/api/books")).andExpect(status().isOk());
        mockMvc.perform(get("/api/cart")).andExpect(status().isOk());
    }

    private MockHttpSession login(String email) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(email, "correct-horse-1")))
            .andExpect(status().isOk());
        return session;
    }

    private void saveUser(String email, Role role, boolean active) {
        AppUser user = new AppUser(email, passwordEncoder.encode("correct-horse-1"), "Test " + role, role);
        user.setActive(active);
        users.saveAndFlush(user);
    }

    private static String registerJson(String email, String role) {
        return "{\"email\":\"" + email + "\",\"password\":\"correct-horse-1\",\"display_name\":\"Test Reader\""
            + (role == null ? "" : ",\"role\":\"" + role + "\"") + "}";
    }

    private static String loginJson(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }
}
