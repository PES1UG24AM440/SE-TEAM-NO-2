package com.bookstore.auth;

import com.bookstore.auth.dto.RegistrationRequest;
import com.bookstore.auth.dto.RegistrationResponse;
import com.bookstore.auth.dto.AuthenticatedUserResponse;
import com.bookstore.auth.dto.LoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final UserRepository users;

    public AuthController(
            RegistrationService registrationService,
            LoginService loginService,
            UserRepository users) {
        this.registrationService = registrationService;
        this.loginService = loginService;
        this.users = users;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken csrfToken) {
        return Map.of(
            "token", csrfToken.getToken(),
            "headerName", csrfToken.getHeaderName()
        );
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse register(@Valid @RequestBody RegistrationRequest request) {
        return registrationService.register(request);
    }

    @PostMapping("/login")
    public AuthenticatedUserResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        return loginService.login(request, servletRequest, servletResponse);
    }

    @GetMapping("/me")
    public AuthenticatedUserResponse me(Authentication authentication) {
        User user = users.findByEmail(authentication.getName()
                .trim()
                .toLowerCase(Locale.ROOT))
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "The authenticated account is no longer available."
            ));
        return new AuthenticatedUserResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getRole()
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }
}
