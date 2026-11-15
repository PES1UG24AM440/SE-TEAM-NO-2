package com.bookstore.admin;

import com.bookstore.admin.dto.AdminSummaryResponse;
import com.bookstore.admin.dto.AuditLogResponse;
import com.bookstore.books.Book;
import com.bookstore.books.ListingRequest;
import com.bookstore.orders.OrderService;
import com.bookstore.orders.dto.AdminOrderResponse;
import com.bookstore.users.CurrentUserService;
import com.bookstore.users.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Administrator API (#73). SecurityConfig rejects anyone without the ADMIN role before a request
 * reaches this class (401 when not signed in, 403 for customers and sellers). Each method also
 * re-checks the account in the database, so a deactivated admin is blocked straight away.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService admin;
    private final OrderService orderService;
    private final CurrentUserService currentUser;

    public AdminController(AdminService admin, OrderService orderService, CurrentUserService currentUser) {
        this.admin = admin;
        this.orderService = orderService;
        this.currentUser = currentUser;
    }

    @GetMapping("/summary")
    public AdminSummaryResponse summary() {
        currentUser.require();
        return admin.summary();
    }

    @GetMapping("/users")
    public List<UserResponse> users() {
        currentUser.require();
        return admin.listUsers();
    }

    @PostMapping("/users/{id}/activate")
    public UserResponse activateUser(@PathVariable Long id) {
        return admin.setUserActive(currentUser.require(), id, true);
    }

    @PostMapping("/users/{id}/deactivate")
    public UserResponse deactivateUser(@PathVariable Long id) {
        return admin.setUserActive(currentUser.require(), id, false);
    }

    @GetMapping("/books")
    public List<Book> books() {
        currentUser.require();
        return admin.listBooks();
    }

    @PutMapping("/books/{id}")
    public Book updateBook(@PathVariable Long id, @Valid @RequestBody ListingRequest request) {
        return admin.updateBook(currentUser.require(), id, request);
    }

    @PostMapping("/books/{id}/activate")
    public Book activateBook(@PathVariable Long id) {
        return admin.setBookActive(currentUser.require(), id, true);
    }

    @PostMapping("/books/{id}/deactivate")
    public Book deactivateBook(@PathVariable Long id) {
        return admin.setBookActive(currentUser.require(), id, false);
    }

    @GetMapping("/orders")
    public List<AdminOrderResponse> orders() {
        currentUser.require();
        return orderService.getAllOrdersForAdmin();
    }

    @GetMapping("/orders/{orderId}")
    public AdminOrderResponse order(@PathVariable String orderId) {
        currentUser.require();
        return orderService.getOrderForAdmin(orderId);
    }

    @GetMapping("/audit-log")
    public List<AuditLogResponse> auditLog() {
        currentUser.require();
        return admin.recentAuditEntries();
    }
}
