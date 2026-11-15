package com.bookstore.orders.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerDetailsRequest(
    @NotBlank
    @Size(max = 120)
    String name,

    @NotBlank
    @Email
    @Size(max = 254)
    String email,

    @Size(max = 30)
    @Pattern(
        regexp = "^\\s*$|^(?=(?:\\D*\\d){7,15}\\D*$)\\+?[0-9 ()-]{7,25}$",
        message = "Enter a valid phone number."
    )
    String phone,

    @NotBlank
    @Size(max = 200)
    String address_line,

    @NotBlank
    @Size(max = 100)
    String city,

    @NotBlank
    @Size(max = 100)
    String state,

    @NotBlank
    @Size(max = 20)
    @Pattern(
        regexp = "^[\\p{L}\\p{N}](?:[\\p{L}\\p{N} -]{0,18}[\\p{L}\\p{N}])?$",
        message = "Enter a valid postal code."
    )
    String postal_code,

    @NotBlank
    @Size(max = 100)
    String country
) {}
