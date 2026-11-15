package com.bookstore.auth;

import org.springframework.http.HttpStatus;

public class RegistrationException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private RegistrationException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static RegistrationException duplicateEmail() {
        return new RegistrationException(
            HttpStatus.CONFLICT,
            "email_already_registered",
            "An account with this email already exists."
        );
    }

    public static RegistrationException passwordTooLong() {
        return new RegistrationException(
            HttpStatus.BAD_REQUEST,
            "validation_error",
            "Password must be 72 bytes or fewer."
        );
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
