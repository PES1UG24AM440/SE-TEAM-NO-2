package com.bookstore.users;

import com.bookstore.orders.OrderException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Looks up the logged-in account in the database on every protected call, so an account an
 * administrator has just deactivated is blocked immediately, even if its session is still open.
 */
@Service
public class CurrentUserService {

    private final UserRepository users;

    public CurrentUserService(UserRepository users) {
        this.users = users;
    }

    public AppUser require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new OrderException(401, "unauthenticated", "Please sign in to continue.");
        }

        AppUser user = users.findByEmailIgnoreCase(auth.getName())
            .orElseThrow(() -> new OrderException(401, "unauthenticated", "Please sign in to continue."));

        if (!user.isActive()) {
            throw new OrderException(403, "account_disabled", "This account has been deactivated.");
        }
        return user;
    }
}
