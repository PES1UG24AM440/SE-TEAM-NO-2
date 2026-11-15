package com.bookstore.books;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body of POST/PUT /api/seller/books and PUT /api/admin/books/{id}.
 * Supports structured categoryId as well as category name/slug (#46).
 */
public record ListingRequest(
    @NotBlank(message = "Title is required.")
    @Size(max = 200, message = "Title must be 200 characters or fewer.")
    String title,

    @NotBlank(message = "Author is required.")
    @Size(max = 160, message = "Author must be 160 characters or fewer.")
    String author,

    @Size(max = 100, message = "Category must be 100 characters or fewer.")
    String category,

    Long categoryId,

    @Size(max = 2000, message = "Description must be 2000 characters or fewer.")
    String description,

    @NotNull(message = "Price is required.")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0.")
    @Digits(integer = 8, fraction = 2, message = "Price must have at most 2 decimal places.")
    BigDecimal price,

    @NotNull(message = "Stock is required.")
    @Min(value = 0, message = "Stock cannot be negative.")
    @Max(value = 100000, message = "Stock must be 100000 or fewer.")
    Integer stock
) {
    public ListingRequest(
            String title,
            String author,
            String category,
            String description,
            BigDecimal price,
            Integer stock) {
        this(title, author, category, null, description, price, stock);
    }
}