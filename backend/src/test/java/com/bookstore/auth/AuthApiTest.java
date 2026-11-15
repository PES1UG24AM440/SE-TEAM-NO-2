package com.bookstore.auth;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("null")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:orders;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class AuthApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clearUsers() {
        users.deleteAll();
    }

    private static RequestPostProcessor csrfToken() {
        return csrf();
    }

    @Test
    void loginPersistsSessionAndLogoutInvalidatesIt() throws Exception {

        saveUser("customer@example.com", UserRole.CUSTOMER);

        var login = mockMvc.perform(post("/api/auth/login")
                        .with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"email":"CUSTOMER@example.com","password":"SecurePass1!"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("customer@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        HttpSession sessionObj = login.getRequest().getSession(false);

        assertThat(sessionObj).isNotNull();
        assertThat(sessionObj).isInstanceOf(MockHttpSession.class);

        MockHttpSession session = (MockHttpSession) sessionObj;

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Customer"))
                .andExpect(jsonPath("$.email").value("customer@example.com"));

        mockMvc.perform(post("/api/auth/logout")
                        .with(csrfToken())
                        .session(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail.code").value("unauthorized"));
    }

    @Test
    void rejectsInvalidCredentialsWithJsonUnauthorizedResponse() throws Exception {

        saveUser("customer@example.com", UserRole.CUSTOMER);

        mockMvc.perform(post("/api/auth/login")
                        .with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"email":"customer@example.com","password":"WrongPass1!"}
                            """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail.code").value("invalid_credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"email":"customer@example.com","password":"%s"}
                            """.formatted("é".repeat(37))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail.code").value("invalid_credentials"));
    }

    @Test
    void requiresCsrfTokensForUnsafeRequests() throws Exception {

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"email":"customer@example.com","password":"SecurePass1!"}
                            """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail.code").value("forbidden"));
    }

    @Test
    void protectsSellerAndAdminRoutesByRoleAndRejectsAnonymousRequests() throws Exception {

        MockHttpSession customerSession =
                login("customer@example.com", UserRole.CUSTOMER);

        MockHttpSession sellerSession =
                login("seller@example.com", UserRole.SELLER);

        MockHttpSession adminSession =
                login("admin@example.com", UserRole.ADMIN);

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/seller/inventory")
                        .session(customerSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/users")
                        .session(sellerSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/seller/inventory")
                        .session(sellerSession))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/admin/users")
                        .session(adminSession))
                .andExpect(status().isNotFound());
    }

    @Test
    void registrationAlwaysAssignsCustomerRole() throws Exception {

        mockMvc.perform(post("/api/auth/register")
                        .with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"Ada Reader","email":"ada@example.com","password":"SecurePass1!","role":"ADMIN"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").doesNotExist());

        assertThat(
                users.findByEmail("ada@example.com")
                        .orElseThrow()
                        .getRole()
        ).isEqualTo(UserRole.CUSTOMER);
    }

    private MockHttpSession login(String email, UserRole role) throws Exception {

        saveUser(email, role);

        HttpSession session = mockMvc.perform(post("/api/auth/login")
                        .with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"email":"%s","password":"SecurePass1!"}
                            """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);

        assertThat(session).isNotNull();
        assertThat(session).isInstanceOf(MockHttpSession.class);

        return (MockHttpSession) session;
    }

    private void saveUser(String email, UserRole role) {

        users.saveAndFlush(
                new User(
                        "Test Customer",
                        email,
                        passwordEncoder.encode("SecurePass1!"),
                        role
                )
        );
    }
}