package com.warehouse.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ProductRequest(
        @NotBlank(message = "SKU is required")
        @Size(max = 40, message = "SKU must be at most 40 characters")
        String sku,

        @NotBlank(message = "Product name is required")
        @Size(max = 120, message = "Name must be at most 120 characters")
        String name,

        @NotBlank(message = "Category is required")
        @Size(max = 60, message = "Category must be at most 60 characters")
        String category,

        @NotNull(message = "Price is required")
        @PositiveOrZero(message = "Price cannot be negative")
        Double price,

        @Size(max = 500, message = "Description must be at most 500 characters")
        String description,

        Boolean active
) {
}
