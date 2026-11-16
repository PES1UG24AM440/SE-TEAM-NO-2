package com.bookstore.books;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Order(1)
public class CategorySeeder implements ApplicationRunner {

    private final CategoryRepository categories;

    public CategorySeeder(CategoryRepository categories) {
        this.categories = categories;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Category> defaults = List.of(
                new Category("Fiction", "fiction", "Literary and contemporary fiction works."),
                new Category("Self-Help", "self-help", "Personal development, habits, and productivity."),
                new Category("Technology", "technology", "Software engineering, computing, and systems."),
                new Category("Fantasy", "fantasy", "Speculative and epic fantasy worlds."),
                new Category("Programming", "programming", "Software development languages, algorithms, and design patterns."),
                new Category("Uncategorized", "uncategorized", "General books awaiting specific genre classification.")
        );

        for (Category item : defaults) {
            if (!categories.existsByNameIgnoreCase(item.getName())
                    && !categories.existsBySlugIgnoreCase(item.getSlug())) {
                categories.save(item);
            }
        }
    }
}