package com.bookstore.auth;

import org.springframework.http.HttpStatus;

public class LoginException extends RuntimeException {

    private final HttpStatus status = HttpStatus.UNAUTHORIZED;

    private LoginException() {
        super("Invalid email or password.");
    }

    public static LoginException invalidCredentials() {
        return new LoginException();
    }

    public HttpStatus getStatus() { return status; }
}
