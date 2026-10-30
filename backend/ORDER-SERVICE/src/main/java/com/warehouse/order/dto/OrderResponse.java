package com.warehouse.order.dto;

import com.warehouse.order.entity.Order;

import java.time.Instant;

public record OrderResponse(
        Long id,
        String orderNumber,
        String customerName,
        String customerEmail,
        Long productId,
        String productName,
        Long warehouseId,
        Integer quantity,
        Double unitPrice,
        Double totalAmount,
        String status,
        Instant createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getCustomerName(),
                order.getCustomerEmail(),
                order.getProductId(),
                order.getProductName(),
                order.getWarehouseId(),
                order.getQuantity(),
                order.getUnitPrice(),
                order.getTotalAmount(),
                order.getStatus().name(),
                order.getCreatedAt()
        );
    }
}
