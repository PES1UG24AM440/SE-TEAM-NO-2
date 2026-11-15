package com.bookstore.books;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:cataloguetest;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class CatalogueApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookRepository books;

    @Autowired
    private CategoryRepository categories;

    @Test
    void testBookRetrievalAndSearchFilters() throws Exception {
        Category cat = categories.findByNameIgnoreCase("Programming")
                .orElseGet(() -> categories.save(new Category("Programming", "programming", "Programming")));

        Book b1 = new Book();
        b1.setTitle("Advanced Java Programming");
        b1.setAuthor("Author A");
        b1.setCategoryEntity(cat);
        b1.setDescription("Deep dive into advanced Java concepts.");
        b1.setPrice(new BigDecimal("599.00"));
        b1.setStock(10);
        b1.setActive(true);
        books.saveAndFlush(b1);

        Book b2 = new Book();
        b2.setTitle("Intro to Python");
        b2.setAuthor("Author B");
        b2.setCategoryEntity(cat);
        b2.setDescription("Beginner friendly python guide.");
        b2.setPrice(new BigDecimal("299.00"));
        b2.setStock(5);
        b2.setActive(true);
        books.saveAndFlush(b2);

        // Test unpaginated retrieval & general search query
        mockMvc.perform(get("/api/books")
                        .param("q", "Java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].title", containsString("Java")));

        // Test filtering by categoryId
        mockMvc.perform(get("/api/books")
                        .param("categoryId", String.valueOf(cat.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));

        // Test filtering by category slug/name
        mockMvc.perform(get("/api/books")
                        .param("category", "programming"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    void testPaginationAndSorting() throws Exception {
        Category cat = categories.findByNameIgnoreCase("Technology")
                .orElseGet(() -> categories.save(new Category("Technology", "technology", "Technology")));

        for (int i = 1; i <= 5; i++) {
            Book b = new Book();
            b.setTitle("Book " + i);
            b.setAuthor("Author " + i);
            b.setCategoryEntity(cat);
            b.setPrice(new BigDecimal(100 * i + ".00"));
            b.setStock(10);
            b.setActive(true);
            books.saveAndFlush(b);
        }

        // Test paged response structure and sorting by price asc
        mockMvc.perform(get("/api/books")
                        .param("page", "0")
                        .param("size", "2")
                        .param("sortBy", "price")
                        .param("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(5)))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.sortBy").value("price"))
                .andExpect(jsonPath("$.direction").value("asc"));
    }

    @Test
    void testInvalidParametersAndIds() throws Exception {
        // Negative page index -> 400 Bad Request
        mockMvc.perform(get("/api/books")
                        .param("page", "-1")
                        .param("size", "5"))
                .andExpect(status().isBadRequest());

        // Invalid page size (0) -> 400 Bad Request
        mockMvc.perform(get("/api/books")
                        .param("page", "0")
                        .param("size", "0"))
                .andExpect(status().isBadRequest());

        // Unsupported sort field -> 400 Bad Request
        mockMvc.perform(get("/api/books")
                        .param("sortBy", "invalidField"))
                .andExpect(status().isBadRequest());

        // Non-existent book ID -> 404 Not Found
        mockMvc.perform(get("/api/books/999999"))
                .andExpect(status().isNotFound());

        // Invalid book ID format -> 400 Bad Request
        mockMvc.perform(get("/api/books/abc"))
                .andExpect(status().isBadRequest());
    }
}