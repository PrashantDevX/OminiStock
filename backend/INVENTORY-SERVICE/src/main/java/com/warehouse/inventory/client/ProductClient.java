package com.warehouse.inventory.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Order -> Inventory -> Product chain:
 * inventory validates every product reference against the catalog service.
 */
@FeignClient(name = "PRODUCT-SERVICE")
public interface ProductClient {

    @GetMapping("/products/{id}")
    ProductDto getProduct(@PathVariable("id") Long id);

    record ProductDto(Long id, String sku, String name, Double price) {
    }
}
