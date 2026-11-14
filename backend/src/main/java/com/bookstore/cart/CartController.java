package com.bookstore.cart;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;

@RestController("cartLineController")
@RequestMapping("/api/cart/{cartId}")
public class CartController {
    private final CartService cart;
    public CartController(CartService cart) { this.cart = cart; }

    @GetMapping
    public CartResponse get(@PathVariable String cartId) { return cart.get(cartId); }

    @PutMapping("/items/{bookId}")
    public CartResponse update(@PathVariable String cartId, @PathVariable Long bookId, @Valid @RequestBody QuantityRequest request) {
        return cart.update(cartId, bookId, request.quantity());
    }

    @DeleteMapping("/items/{bookId}")
    public CartResponse remove(@PathVariable String cartId, @PathVariable Long bookId) { return cart.remove(cartId, bookId); }

    @DeleteMapping("/items")
    public CartResponse clear(@PathVariable String cartId) { return cart.clear(cartId); }

    public record QuantityRequest(@NotNull @Positive Integer quantity) {}
}
