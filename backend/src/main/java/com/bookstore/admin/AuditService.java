package com.bookstore.admin;

import com.bookstore.users.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes audit entries. MANDATORY propagation means it must run inside the admin action's own
 * transaction, so the change and its audit entry are saved together or not at all.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLog;

    public AuditService(AuditLogRepository auditLog) {
        this.auditLog = auditLog;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AppUser actor, String action, String targetType, Object targetId, String details) {
        String safeDetails = details == null ? null : details.length() > 500 ? details.substring(0, 500) : details;
        auditLog.save(new AuditLog(
            actor.getId(), actor.getEmail(), action, targetType, String.valueOf(targetId), safeDetails));
    }
}
