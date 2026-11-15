package com.bookstore.auth;

import com.bookstore.auth.dto.RegistrationRequest;
import com.bookstore.auth.dto.RegistrationResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Locale;

@Service
public class RegistrationService {

    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegistrationResponse register(RegistrationRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String name = request.name().trim();

        if (request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw RegistrationException.passwordTooLong();
        }
        if (users.existsByEmail(email)) {
            throw RegistrationException.duplicateEmail();
        }

        User user = new User(
            name,
            email,
            passwordEncoder.encode(request.password()),
            UserRole.CUSTOMER
        );
        try {
            User saved = users.saveAndFlush(user);
            return new RegistrationResponse(saved.getId(), saved.getName(), saved.getEmail());
        } catch (DataIntegrityViolationException exception) {
            if (isUniqueConstraintViolation(exception)) {
                throw RegistrationException.duplicateEmail();
            }
            throw exception;
        }
    }

    private boolean isUniqueConstraintViolation(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException
                    && (sqlException.getErrorCode() == 1062
                    || "23505".equals(sqlException.getSQLState()))) {
                return true;
            }
        }
        return false;
    }
}
