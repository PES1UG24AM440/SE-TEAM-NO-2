package com.bookstore.books;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:reviewtest;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class BookReviewIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookRepository books;

    @Autowired
    private CategoryRepository categories;

    @Test
    void submitAndRetrieveReviewsCalculatesAverageRating() throws Exception {
        Category cat = categories.findByNameIgnoreCase("Fiction")
                .orElseGet(() -> categories.save(new Category("Fiction", "fiction", "Fiction")));

        Book book = new Book();
        book.setTitle("Reviewable Book");
        book.setAuthor("Test Author");
        book.setCategoryEntity(cat);
        book.setPrice(new BigDecimal("299.00"));
        book.setStock(10);
        book.setActive(true);
        Book saved = books.saveAndFlush(book);

        mockMvc.perform(post("/api/books/" + saved.getId() + "/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating": 5, "comment": "Superb pacing and depth!", "reviewerName": "Rishi"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookId").value(saved.getId()))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.reviewerName").value("Rishi"))
                .andExpect(jsonPath("$.comment").value("Superb pacing and depth!"));

        mockMvc.perform(post("/api/books/" + saved.getId() + "/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating": 4, "comment": "Very informative read.", "reviewerName": "Avyukth"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/books/" + saved.getId() + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookId").value(saved.getId()))
                .andExpect(jsonPath("$.reviewCount").value(2))
                .andExpect(jsonPath("$.averageRating").value(4.5))
                .andExpect(jsonPath("$.reviews", hasSize(2)));
    }

    @Test
    void rejectsEmptyReviewsAndOutOfRangeRatings() throws Exception {
        Category cat = categories.findByNameIgnoreCase("Fiction")
                .orElseGet(() -> categories.save(new Category("Fiction", "fiction", "Fiction")));

        Book book = new Book();
        book.setTitle("Validation Book");
        book.setAuthor("Test Author");
        book.setCategoryEntity(cat);
        book.setPrice(new BigDecimal("199.00"));
        book.setStock(5);
        book.setActive(true);
        Book saved = books.saveAndFlush(book);

        mockMvc.perform(post("/api/books/" + saved.getId() + "/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating": 0, "comment": "   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail.code").value("validation_error"))
                .andExpect(jsonPath("$.detail.fields[*].field", containsInAnyOrder("rating", "comment")));

        mockMvc.perform(post("/api/books/" + saved.getId() + "/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating": 6, "comment": "Too high rating"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail.code").value("validation_error"));

        mockMvc.perform(get("/api/books/999999/reviews"))
                .andExpect(status().isNotFound());
    }
}