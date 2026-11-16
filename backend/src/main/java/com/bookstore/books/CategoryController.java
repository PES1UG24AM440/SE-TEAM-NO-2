package com.bookstore.books;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Map;

@RestController
public class CategoryController {

    private final CategoryService categoryService;
    private final BookController bookController;

    public CategoryController(CategoryService categoryService, BookController bookController) {
        this.categoryService = categoryService;
        this.bookController = bookController;
    }

    @GetMapping("/api/categories")
    public List<CategoryResponse> getCategories() {
        return categoryService.listAll();
    }

    @GetMapping("/api/categories/{id}")
    public CategoryResponse getCategory(@PathVariable Long id) {
        return categoryService.getById(id);
    }

    @GetMapping("/api/categories/{id}/books")
    public Object getBooksByCategory(
            @PathVariable Long id,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "title") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {
        return bookController.getBooks(q, title, author, null, id, page, size, sortBy, direction);
    }

    @PostMapping("/api/admin/categories")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(request));
    }

    @PutMapping("/api/admin/categories/{id}")
    public CategoryResponse updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request) {
        return categoryService.update(id, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleInvalidCategoryId(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Invalid category ID format: must be a numeric ID"));
    }
}