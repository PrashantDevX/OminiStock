package com.warehouse.inventory.dto;

import com.warehouse.inventory.entity.Inventory;

import java.time.Instant;

public record InventoryResponse(
        Long id,
        Long productId,
        Long warehouseId,
        Integer quantity,
        Integer reservedQuantity,
        Integer availableQuantity,
        Integer reorderLevel,
        boolean lowStock,
        Instant updatedAt
) {
    public static InventoryResponse from(Inventory inventory) {
        return new InventoryResponse(
                inventory.getId(),
                inventory.getProductId(),
                inventory.getWarehouseId(),
                inventory.getQuantity(),
                inventory.getReservedQuantity(),
                inventory.getAvailableQuantity(),
                inventory.getReorderLevel(),
                inventory.isLowStock(),
                inventory.getUpdatedAt()
        );
    }
}
