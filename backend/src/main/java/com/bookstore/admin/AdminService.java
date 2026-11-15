package com.bookstore.admin;

import com.bookstore.admin.dto.AdminSummaryResponse;
import com.bookstore.admin.dto.AuditLogResponse;
import com.bookstore.books.Book;
import com.bookstore.books.BookRepository;
import com.bookstore.books.ListingRequest;
import com.bookstore.books.ListingService;
import com.bookstore.orders.OrderException;
import com.bookstore.orders.OrderRepository;
import com.bookstore.orders.OrderStatus;
import com.bookstore.users.AppUser;
import com.bookstore.users.Role;
import com.bookstore.users.UserRepository;
import com.bookstore.users.dto.UserResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Administrator functions (#73). Every change runs in one transaction together with its audit entry.
 * Listing changes reuse ListingService from #72 instead of duplicating that logic.
 */
@Service
public class AdminService {

    private final UserRepository users;
    private final BookRepository books;
    private final OrderRepository orders;
    private final ListingService listings;
    private final AuditService audit;
    private final AuditLogRepository auditLog;

    public AdminService(
            UserRepository users,
            BookRepository books,
            OrderRepository orders,
            ListingService listings,
            AuditService audit,
            AuditLogRepository auditLog) {
        this.users = users;
        this.books = books;
        this.orders = orders;
        this.listings = listings;
        this.audit = audit;
        this.auditLog = auditLog;
    }

    @Transactional(readOnly = true)
    public AdminSummaryResponse summary() {
        long totalBooks = books.count();
        long activeBooks = books.countByActiveTrue();
        BigDecimal revenue = orders.sumTotalByStatus(OrderStatus.CONFIRMED);
        return new AdminSummaryResponse(
            new AdminSummaryResponse.BookSummary(
                totalBooks, activeBooks, totalBooks - activeBooks, books.countByActiveTrueAndStock(0)),
            new AdminSummaryResponse.UserSummary(
                users.count(),
                users.countByRole(Role.CUSTOMER),
                users.countByRole(Role.SELLER),
                users.countByRole(Role.ADMIN),
                users.countByActiveFalse()),
            new AdminSummaryResponse.OrderSummary(
                orders.count(),
                orders.countByStatus(OrderStatus.CONFIRMED),
                orders.countByStatus(OrderStatus.CANCELLED),
                (revenue == null ? BigDecimal.ZERO : revenue).setScale(2, RoundingMode.HALF_UP)));
    }

    // ---- users ----

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return users.findAllByOrderByIdAsc().stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse setUserActive(AppUser admin, Long userId, boolean active) {
        if (Objects.equals(admin.getId(), userId)) {
            throw new OrderException(409, "cannot_change_own_account",
                "You cannot deactivate or reactivate your own account.");
        }
        AppUser target = users.findById(userId)
            .orElseThrow(() -> new OrderException(404, "user_not_found", "User " + userId + " was not found."));

        if (target.isActive() != active) {
            target.setActive(active);
            users.saveAndFlush(target);
            audit.record(admin, active ? "USER_ACTIVATED" : "USER_DEACTIVATED", "USER", target.getId(),
                target.getEmail() + " (" + target.getRole() + ")");
        }
        return UserResponse.from(target);
    }

    // ---- listings ----

    @Transactional(readOnly = true)
    public List<Book> listBooks() {
        return listings.listAll();
    }

    @Transactional
    public Book updateBook(AppUser admin, Long bookId, ListingRequest request) {
        Book updated = listings.update(bookId, request);
        audit.record(admin, "LISTING_UPDATED", "BOOK", updated.getId(),
            "'" + updated.getTitle() + "' price " + updated.getPrice() + ", stock " + updated.getStock());
        return updated;
    }

    @Transactional
    public Book setBookActive(AppUser admin, Long bookId, boolean active) {
        Book updated = listings.setActive(bookId, active);
        audit.record(admin, active ? "LISTING_ACTIVATED" : "LISTING_DEACTIVATED", "BOOK", updated.getId(),
            "'" + updated.getTitle() + "'");
        return updated;
    }

    // ---- audit log ----

    @Transactional(readOnly = true)
    public List<AuditLogResponse> recentAuditEntries() {
        return auditLog.findTop200ByOrderByIdDesc().stream().map(AuditLogResponse::from).toList();
    }
}
