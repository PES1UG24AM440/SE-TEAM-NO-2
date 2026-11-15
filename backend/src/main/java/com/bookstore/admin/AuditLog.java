package com.bookstore.admin;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * One row per administrative action (#73): who did it, what they did, to what, and when.
 * Rows are only ever inserted, never updated or deleted by the application.
 * The actor's email is copied in so the entry stays readable even if the account changes.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Column(name = "actor_email", nullable = false, length = 254)
    private String actorEmail;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    @Column(name = "target_id", nullable = false, length = 64)
    private String targetId;

    @Column(length = 500)
    private String details;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {}

    public AuditLog(Long actorUserId, String actorEmail, String action, String targetType, String targetId, String details) {
        this.actorUserId = actorUserId;
        this.actorEmail = actorEmail;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.details = details;
    }

    public Long getId() { return id; }
    public Long getActorUserId() { return actorUserId; }
    public String getActorEmail() { return actorEmail; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
