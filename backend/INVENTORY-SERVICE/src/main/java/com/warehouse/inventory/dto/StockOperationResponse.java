package com.warehouse.inventory.dto;

public record StockOperationResponse(
        boolean success,
        String message,
        int availableQuantity
) {
    public static StockOperationResponse ok(String message, int available) {
        return new StockOperationResponse(true, message, available);
    }

    public static StockOperationResponse rejected(String message, int available) {
        return new StockOperationResponse(false, message, available);
    }
}
