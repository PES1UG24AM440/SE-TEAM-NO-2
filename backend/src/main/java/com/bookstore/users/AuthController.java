package com.bookstore.users;

import com.bookstore.orders.OrderException;
import com.bookstore.orders.OrderRepository;
import com.bookstore.users.dto.LoginRequest;
import com.bookstore.users.dto.RegisterRequest;
import com.bookstore.users.dto.UserResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

/**
 * POST /api/auth/register, POST /api/auth/login, GET /api/auth/me.
 * Logout (POST /api/auth/logout) is handled by Spring Security in SecurityConfig.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final CurrentUserService currentUser;
    private final OrderRepository orders;

    public AuthController(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            CurrentUserService currentUser,
            OrderRepository orders) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.currentUser = currentUser;
        this.orders = orders;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        Role role = parseSelfServiceRole(request.role());
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (users.existsByEmailIgnoreCase(email)) {
            throw new OrderException(409, "email_taken", "An account with this email already exists.");
        }

        try {
            AppUser saved = users.saveAndFlush(new AppUser(
                email,
                passwordEncoder.encode(request.password()),
                request.display_name().trim(),
                role
            ));
            return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(saved));
        } catch (DataIntegrityViolationException duplicate) {
            throw new OrderException(409, "email_taken", "An account with this email already exists.");
        }
    }

    @PostMapping("/login")
    public UserResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                    request.email().trim(), request.password()));
        } catch (AuthenticationException failed) {
            // Same message for unknown email, wrong password and disabled account (SRS OBS-F-002).
            throw new OrderException(401, "invalid_credentials", "Email or password is incorrect.");
        }

        // Session-fixation protection: give the browser a new session ID after login.
        // Guest orders are keyed by session ID, so move them to the new ID; the cart lives
        // in a session attribute and is kept automatically.
        HttpSession existing = httpRequest.getSession(false);
        if (existing != null) {
            String oldId = existing.getId();
            String newId = httpRequest.changeSessionId();
            orders.reassignOwnerSession(oldId, newId);
        } else {
            httpRequest.getSession(true);
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        AppUser user = users.findByEmailIgnoreCase(authentication.getName())
            .orElseThrow(() -> new OrderException(401, "invalid_credentials", "Email or password is incorrect."));
        return UserResponse.from(user);
    }

    @GetMapping("/me")
    public UserResponse me() {
        return UserResponse.from(currentUser.require());
    }

    private Role parseSelfServiceRole(String raw) {
        if (raw == null || raw.isBlank()) {
            return Role.CUSTOMER;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (value.equals(Role.CUSTOMER.name())) return Role.CUSTOMER;
        if (value.equals(Role.SELLER.name())) return Role.SELLER;
        throw new OrderException(400, "invalid_role", "Choose either a customer or a seller account.");
    }
}
