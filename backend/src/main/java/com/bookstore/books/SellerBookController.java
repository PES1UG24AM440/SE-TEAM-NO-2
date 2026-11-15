package com.bookstore.books;

import com.bookstore.users.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Seller listing management (#72). SecurityConfig only lets SELLER accounts reach /api/seller/**;
 * ListingService then checks that the listing belongs to the signed-in seller.
 */
@RestController
@RequestMapping("/api/seller/books")
public class SellerBookController {

    private final ListingService listings;
    private final CurrentUserService currentUser;

    public SellerBookController(ListingService listings, CurrentUserService currentUser) {
        this.listings = listings;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<Book> myListings() {
        return listings.listForSeller(currentUser.require().getId());
    }

    @PostMapping
    public ResponseEntity<Book> create(@Valid @RequestBody ListingRequest request) {
        Book created = listings.create(currentUser.require().getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public Book update(@PathVariable Long id, @Valid @RequestBody ListingRequest request) {
        return listings.updateOwn(currentUser.require().getId(), id, request);
    }

    /** "Remove" is a soft delete: past orders still reference the book, so the row is kept. */
    @DeleteMapping("/{id}")
    public Book deactivate(@PathVariable Long id) {
        return listings.setActiveOwn(currentUser.require().getId(), id, false);
    }

    @PostMapping("/{id}/activate")
    public Book activate(@PathVariable Long id) {
        return listings.setActiveOwn(currentUser.require().getId(), id, true);
    }
}
