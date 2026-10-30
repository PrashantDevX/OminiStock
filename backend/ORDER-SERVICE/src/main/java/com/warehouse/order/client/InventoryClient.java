package com.warehouse.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Order -> Inventory hop of the Order -> Inventory -> Product chain.
 * Spring Cloud LoadBalancer picks an INVENTORY-SERVICE instance registered in Eureka.
 */
@FeignClient(name = "INVENTORY-SERVICE")
public interface InventoryClient {

    @PostMapping("/inventory/reserve")
    StockOperationResponse reserve(@RequestBody StockMovementRequest request);

    @PostMapping("/inventory/release")
    StockOperationResponse release(@RequestBody StockMovementRequest request);

    record StockMovementRequest(Long productId, Long warehouseId, Integer quantity) {
    }

    record StockOperationResponse(boolean success, String message, int availableQuantity) {
    }
}
