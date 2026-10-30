package com.warehouse.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record InventoryRequest(
        @NotNull(message = "Product ID is required") Long productId,
        @NotNull(message = "Warehouse ID is required") Long warehouseId,
        @NotNull(message = "Quantity is required")
        @Min(value = 0, message = "Quantity cannot be negative") Integer quantity,
        @NotNull(message = "Reorder level is required")
        @Min(value = 0, message = "Reorder level cannot be negative") Integer reorderLevel
) {
}
