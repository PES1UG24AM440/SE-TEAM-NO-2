package com.bookstore.books;

import com.bookstore.orders.OrderException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
public class CategoryService {

    private final CategoryRepository categories;
    private final BookRepository books;

    public CategoryService(CategoryRepository categories, BookRepository books) {
        this.categories = categories;
        this.books = books;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listAll() {
        return categories.findAllByOrderByNameAsc().stream()
                .map(c -> CategoryResponse.from(c, books.countByCategoryEntity_IdAndActiveTrue(c.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getById(Long id) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category ID must be a positive number");
        }
        Category category = categories.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        long count = books.countByCategoryEntity_IdAndActiveTrue(category.getId());
        return CategoryResponse.from(category, count);
    }

    @Transactional
    public Category resolveForListing(Long categoryId, String categoryNameOrSlug) {
        if (categoryId != null) {
            if (categoryId <= 0) {
                throw new OrderException(400, "invalid_category", "Category ID must be a positive number.");
            }
            Category resolved = categories.findById(categoryId)
                    .orElseThrow(() -> new OrderException(
                            400, "invalid_category", "Category ID " + categoryId + " does not exist."));
            if (categoryNameOrSlug != null && !categoryNameOrSlug.isBlank()
                    && !resolved.getName().equalsIgnoreCase(categoryNameOrSlug.trim())
                    && !resolved.getSlug().equalsIgnoreCase(categoryNameOrSlug.trim())) {
                throw new OrderException(
                        400,
                        "invalid_category",
                        "Category ID " + categoryId + " does not match category '" + categoryNameOrSlug.trim() + "'."
                );
            }
            return resolved;
        }

        if (categoryNameOrSlug == null || categoryNameOrSlug.isBlank()) {
            throw new OrderException(400, "invalid_category", "Category or categoryId is required.");
        }

        String trimmed = categoryNameOrSlug.trim();
        return categories.findByNameOrSlug(trimmed)
                .orElseGet(() -> {
                    String slug = buildSlug(trimmed);
                    return categories.findBySlugIgnoreCase(slug)
                            .orElseGet(() -> categories.saveAndFlush(
                                    new Category(trimmed, slug, "Books and titles in the " + trimmed + " category.")
                            ));
                });
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();
        String slug = buildSlug(request.slug() == null || request.slug().isBlank() ? name : request.slug());
        String description = (request.description() == null || request.description().isBlank())
                ? null : request.description().trim();

        if (categories.existsByNameIgnoreCase(name)) {
            throw new OrderException(409, "duplicate_category", "Category '" + name + "' already exists.");
        }
        if (categories.existsBySlugIgnoreCase(slug)) {
            throw new OrderException(409, "duplicate_category", "Category slug '" + slug + "' already exists.");
        }

        Category saved = categories.saveAndFlush(new Category(name, slug, description));
        return CategoryResponse.from(saved, 0L);
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        if (id == null || id <= 0) {
            throw new OrderException(400, "invalid_category", "Category ID must be a positive number.");
        }
        Category existing = categories.findById(id)
                .orElseThrow(() -> new OrderException(404, "category_not_found", "Category ID " + id + " was not found."));

        String name = request.name().trim();
        String slug = buildSlug(request.slug() == null || request.slug().isBlank() ? name : request.slug());
        String description = (request.description() == null || request.description().isBlank())
                ? null : request.description().trim();

        categories.findByNameIgnoreCase(name)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new OrderException(409, "duplicate_category", "Category '" + name + "' already exists.");
                });

        categories.findBySlugIgnoreCase(slug)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new OrderException(409, "duplicate_category", "Category slug '" + slug + "' already exists.");
                });

        existing.setName(name);
        existing.setSlug(slug);
        existing.setDescription(description);
        Category saved = categories.saveAndFlush(existing);
        long count = books.countByCategoryEntity_IdAndActiveTrue(saved.getId());
        return CategoryResponse.from(saved, count);
    }

    public static String buildSlug(String raw) {
        String slug = raw.trim()
                .toLowerCase(Locale.ROOT)
                .replace("'", "")
                .replace("&", "and")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.isBlank()) {
            throw new OrderException(400, "invalid_category", "Category slug must contain alphanumeric characters.");
        }
        return slug;
    }
}