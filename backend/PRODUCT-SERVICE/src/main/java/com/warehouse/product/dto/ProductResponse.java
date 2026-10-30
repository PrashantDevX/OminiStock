package com.warehouse.product.dto;

import com.warehouse.product.entity.Product;

import java.time.Instant;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        String category,
        Double price,
        String description,
        boolean active,
        Instant createdAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getCategory(),
                product.getPrice(),
                product.getDescription(),
                product.isActive(),
                product.getCreatedAt()
        );
    }
}
