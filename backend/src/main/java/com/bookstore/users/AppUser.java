package com.bookstore.users;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * A registered account. Only a BCrypt hash of the password is stored, never the password itself.
 * Named AppUser because "User" clashes with Spring Security's User class.
 */
@Entity(name = "AppUser")
@Table(name = "users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.CUSTOMER;

    /** Deactivated accounts cannot log in or use seller/admin functions (issue #73). */
    @Column(nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AppUser() {}

    public AppUser(String email, String passwordHash, String displayName, Role role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public Role getRole() { return role; }
    public boolean isActive() { return Boolean.TRUE.equals(active); }
    public Instant getCreatedAt() { return createdAt; }

    public void setActive(boolean active) { this.active = active; }
}
