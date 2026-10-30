package com.warehouse.inventory.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

@Entity
@Table(name = "inventory", uniqueConstraints = {
        @UniqueConstraint(name = "uk_inventory_product_warehouse",
                columnNames = {"product_id", "warehouse_id"})
})
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Product ID is required")
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @NotNull(message = "Warehouse ID is required")
    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    /** Physical units currently held in the warehouse. */
    @NotNull
    @Min(value = 0, message = "Quantity cannot be negative")
    @Column(nullable = false, columnDefinition = "integer default 0")
    private Integer quantity = 0;

    /** Units promised to confirmed orders but not shipped yet. */
    @NotNull
    @Min(value = 0, message = "Reserved quantity cannot be negative")
    @Column(name = "reserved_quantity", nullable = false, columnDefinition = "integer default 0")
    private Integer reservedQuantity = 0;

    @NotNull
    @Min(value = 0, message = "Reorder level cannot be negative")
    @Column(name = "reorder_level", nullable = false, columnDefinition = "integer default 10")
    private Integer reorderLevel = 10;

    private Instant updatedAt = Instant.now();

    public Inventory() {
    }

    public Inventory(Long productId, Long warehouseId, Integer quantity, Integer reorderLevel) {
        this.productId = productId;
        this.warehouseId = warehouseId;
        this.quantity = quantity;
        this.reorderLevel = reorderLevel;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    /** Stock that can still be promised to a new order. */
    @Transient
    public int getAvailableQuantity() {
        return Math.max(0, quantity - reservedQuantity);
    }

    public boolean isLowStock() {
        return getAvailableQuantity() <= reorderLevel;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getReservedQuantity() {
        return reservedQuantity;
    }

    public void setReservedQuantity(Integer reservedQuantity) {
        this.reservedQuantity = reservedQuantity;
    }

    public Integer getReorderLevel() {
        return reorderLevel;
    }

    public void setReorderLevel(Integer reorderLevel) {
        this.reorderLevel = reorderLevel;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
