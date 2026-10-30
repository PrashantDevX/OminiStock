package com.warehouse.order.dto;

public record OrderSummary(
        long totalOrders,
        long confirmed,
        long rejected,
        long cancelled,
        double confirmedRevenue
) {
}
