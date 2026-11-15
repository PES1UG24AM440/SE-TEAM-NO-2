package com.bookstore.auth;

import com.bookstore.auth.dto.AuthenticatedUserResponse;
import com.bookstore.auth.dto.LoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class LoginService {

    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final UserRepository users;
    private final SecurityContextHolderStrategy contextHolderStrategy =
        SecurityContextHolder.getContextHolderStrategy();

    public LoginService(
            AuthenticationManager authenticationManager,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            SecurityContextRepository securityContextRepository,
            UserRepository users) {
        this.authenticationManager = authenticationManager;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.securityContextRepository = securityContextRepository;
        this.users = users;
    }

    public AuthenticatedUserResponse login(
            LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length
                > BCRYPT_MAX_PASSWORD_BYTES) {
            throw LoginException.invalidCredentials();
        }
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                    request.email().trim().toLowerCase(Locale.ROOT),
                    request.password()
                )
            );
        } catch (AuthenticationException exception) {
            throw LoginException.invalidCredentials();
        }

        User user = users.findByEmail(authentication.getName())
            .orElseThrow(LoginException::invalidCredentials);
        servletRequest.getSession(true);
        sessionAuthenticationStrategy.onAuthentication(
            authentication,
            servletRequest,
            servletResponse
        );
        SecurityContext context = contextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);

        return new AuthenticatedUserResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getRole()
        );
    }
}
