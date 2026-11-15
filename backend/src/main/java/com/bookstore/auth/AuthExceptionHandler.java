package com.bookstore.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(basePackageClasses = AuthController.class)
public class AuthExceptionHandler {

    @ExceptionHandler(RegistrationException.class)
    public ResponseEntity<Map<String, Object>> handleRegistrationException(
            RegistrationException exception) {
        return ResponseEntity
            .status(exception.getStatus().value())
            .body(Map.of(
                "detail",
                Map.of(
                    "code", exception.getCode(),
                    "message", exception.getMessage()
                )
            ));
    }

    @ExceptionHandler(LoginException.class)
    public ResponseEntity<Map<String, Object>> handleLoginException(
            LoginException exception) {
        return ResponseEntity
            .status(exception.getStatus().value())
            .body(Map.of(
                "detail",
                Map.of(
                    "code", "invalid_credentials",
                    "message", exception.getMessage()
                )
            ));
    }
}
