package com.bookstore.users;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Creates an administrator and a seller account at startup, but only when their email and
 * password are provided as environment variables (BOOKSTORE_ADMIN_EMAIL / BOOKSTORE_ADMIN_PASSWORD,
 * BOOKSTORE_SELLER_EMAIL / BOOKSTORE_SELLER_PASSWORD). No credentials live in the code.
 * Existing accounts are never changed.
 */
@Component
public class DemoUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    @Value("${bookstore.seed.admin-email:}") private String adminEmail;
    @Value("${bookstore.seed.admin-password:}") private String adminPassword;
    @Value("${bookstore.seed.seller-email:}") private String sellerEmail;
    @Value("${bookstore.seed.seller-password:}") private String sellerPassword;

    public DemoUserSeeder(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed(adminEmail, adminPassword, "Administrator", Role.ADMIN);
        seed(sellerEmail, sellerPassword, "Demo Seller", Role.SELLER);
    }

    private void seed(String email, String password, String name, Role role) {
        if (email == null || email.isBlank()) {
            return;
        }
        if (password == null || password.length() < 8) {
            log.warn("Skipping {} account {}: password must be at least 8 characters.", role, email);
            return;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(normalized)) {
            return;
        }
        users.save(new AppUser(normalized, passwordEncoder.encode(password), name, role));
        log.info("Created {} account {}", role, normalized);
    }
}
