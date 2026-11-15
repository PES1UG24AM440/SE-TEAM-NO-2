package com.bookstore.admin.dto;

import com.bookstore.admin.AuditLog;

import java.time.Instant;

public record AuditLogResponse(
    Long id,
    Long actor_user_id,
    String actor_email,
    String action,
    String target_type,
    String target_id,
    String details,
    Instant created_at
) {
    public static AuditLogResponse from(AuditLog entry) {
        return new AuditLogResponse(
            entry.getId(), entry.getActorUserId(), entry.getActorEmail(), entry.getAction(),
            entry.getTargetType(), entry.getTargetId(), entry.getDetails(), entry.getCreatedAt());
    }
}
