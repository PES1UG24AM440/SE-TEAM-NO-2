package com.bookstore.orders;

import com.bookstore.orders.dto.CartQuantityRequest;
import com.bookstore.orders.dto.CartResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse getCart(HttpSession session) {
        return cartService.getCart(session);
    }

    @PutMapping("/items/{bookId}")
    public CartResponse updateQuantity(
            @PathVariable Long bookId,
            @Valid @RequestBody CartQuantityRequest request,
            HttpSession session) {
        return cartService.updateQuantity(session, bookId, request.quantity());
    }

    @DeleteMapping("/items/{bookId}")
    public CartResponse removeItem(
            @PathVariable Long bookId,
            HttpSession session) {
        return cartService.removeItem(session, bookId);
    }

    @DeleteMapping
    public CartResponse clear(HttpSession session) {
        return cartService.clear(session);
    }
}
