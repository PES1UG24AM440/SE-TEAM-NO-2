package com.bookstore.books;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReviewRequest(
        @NotNull(message = "Rating is required.")
        @Min(value = 1, message = "Rating must be between 1 and 5.")
        @Max(value = 5, message = "Rating must be between 1 and 5.")
        Integer rating,

        @JsonAlias({"review", "reviewText", "text"})
        @NotBlank(message = "Review text cannot be empty.")
        @Size(max = 2000, message = "Review text must be 2000 characters or fewer.")
        String comment,

        @Size(max = 120, message = "Reviewer name must be 120 characters or fewer.")
        String reviewerName
) {}