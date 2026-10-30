package com.warehouse.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WarehouseRequest(
        @NotBlank(message = "Warehouse code is required")
        @Size(max = 20, message = "Code must be at most 20 characters")
        String code,

        @NotBlank(message = "Warehouse name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @Size(max = 80, message = "City must be at most 80 characters")
        String city
) {
}
