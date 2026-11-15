package com.bookstore.auth;

import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:orders;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class RegistrationApiTest {

    private static final String VALID_REQUEST = """
        {"name":"Ada Reader","email":"ada@example.com","password":"SecurePass1!"}
        """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clearUsers() {
        userRepository.deleteAll();
    }

    @Test
    void registersUserAndPersistsOnlyBcryptPasswordHash() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("""
                    {"name":" Ada Reader ","email":"ADA@example.com","password":"SecurePass1!"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.name").value("Ada Reader"))
            .andExpect(jsonPath("$.email").value("ada@example.com"))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User saved = userRepository.findByEmail("ada@example.com").orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("$2");
        assertThat(saved.getPasswordHash()).isNotEqualTo("SecurePass1!");
        assertThat(passwordEncoder.matches("SecurePass1!", saved.getPasswordHash())).isTrue();
    }

    @Test
    void rejectsDuplicateEmailWithoutCreatingAnotherUser() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(VALID_REQUEST))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("""
                    {"name":"Another Reader","email":"ADA@example.com","password":"OtherPass2@"}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail.code").value("email_already_registered"));

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void rejectsMissingFieldsInvalidEmailAndWeakPassword() throws Exception {
        for (String request : new String[] {
                "{}",
                """
                {"name":"Ada Reader","email":"not-an-email","password":"SecurePass1!"}
                """,
                """
                {"name":"Ada Reader","email":"ada@example.com","password":"weak"}
                """
        }) {
            mockMvc.perform(post("/api/auth/register")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail.code").value("validation_error"));
        }

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void rejectsPasswordsLongerThanBcryptByteLimit() throws Exception {
        String password = "Aa1!" + "é".repeat(35);
        String request = """
            {"name":"Ada Reader","email":"ada@example.com","password":"%s"}
            """.formatted(password);

        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(Objects.requireNonNull(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail.code").value("validation_error"));

        assertThat(userRepository.count()).isZero();
    }
}
