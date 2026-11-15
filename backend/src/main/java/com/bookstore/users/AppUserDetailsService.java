package com.bookstore.users;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Tells Spring Security how to load an account (by email) when someone logs in. */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public AppUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        AppUser user = users.findByEmailIgnoreCase(email == null ? "" : email.trim())
            .orElseThrow(() -> new UsernameNotFoundException("Unknown account"));

        return User.withUsername(user.getEmail())
            .password(user.getPasswordHash())
            .roles(user.getRole().name())
            .disabled(!user.isActive())
            .build();
    }
}
