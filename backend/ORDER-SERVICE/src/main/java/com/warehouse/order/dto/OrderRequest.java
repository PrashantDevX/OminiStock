package com.warehouse.order.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrderRequest(
        @NotNull(message = "Product ID is required") Long productId,
        @NotNull(message = "Warehouse ID is required") Long warehouseId,
        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1") Integer quantity,
        @NotBlank(message = "Customer name is required")
        @Size(max = 100, message = "Customer name is too long") String customerName,
        @Email(message = "Customer email is invalid")
        @Size(max = 120, message = "Customer email is too long") String customerEmail
) {
}
