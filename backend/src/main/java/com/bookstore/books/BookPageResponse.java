package com.bookstore.books;

import org.springframework.data.domain.Page;
import java.util.List;

public record BookPageResponse(
        List<Book> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        String sortBy,
        String direction
) {
    public static BookPageResponse from(Page<Book> pageData, String sortBy, String direction) {
        return new BookPageResponse(
                pageData.getContent(),
                pageData.getNumber(),
                pageData.getSize(),
                pageData.getTotalElements(),
                pageData.getTotalPages(),
                pageData.isFirst(),
                pageData.isLast(),
                sortBy,
                direction
        );
    }
}