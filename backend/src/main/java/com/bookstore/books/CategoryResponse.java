package com.bookstore.books;

public record CategoryResponse(
        Long id,
        String name,
        String slug,
        String description,
        long bookCount
) {
    public static CategoryResponse from(Category category, long bookCount) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                bookCount
        );
    }
}