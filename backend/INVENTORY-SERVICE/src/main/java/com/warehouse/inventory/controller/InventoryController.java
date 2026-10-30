package com.warehouse.inventory.controller;

import com.warehouse.inventory.dto.InventoryRequest;
import com.warehouse.inventory.dto.InventoryResponse;
import com.warehouse.inventory.dto.StockMovementRequest;
import com.warehouse.inventory.dto.StockOperationResponse;
import com.warehouse.inventory.service.InventoryService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    @Value("${server.port:0}")
    private String instancePort;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public ResponseEntity<List<InventoryResponse>> getAll(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long warehouseId) {

        if (productId != null) {
            return ResponseEntity.ok(inventoryService.findByProduct(productId));
        }
        if (warehouseId != null) {
            return ResponseEntity.ok(inventoryService.findByWarehouse(warehouseId));
        }
        return ResponseEntity.ok(inventoryService.findAll());
    }

    @GetMapping("/low-stock")
    public ResponseEntity<List<InventoryResponse>> lowStock() {
        return ResponseEntity.ok(inventoryService.findLowStock());
    }

    /**
     * Demo endpoint for load balancing: call it repeatedly through the gateway
     * and the port of the serving instance is returned.
     */
    @GetMapping("/instance")
    public ResponseEntity<Map<String, String>> instance(HttpServletResponse response) {
        response.setHeader("X-Instance-Port", instancePort);
        return ResponseEntity.ok(Map.of(
                "service", "INVENTORY-SERVICE",
                "instance", instancePort
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InventoryResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.findById(id));
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<List<InventoryResponse>> getByProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(inventoryService.findByProduct(productId));
    }

    @GetMapping("/warehouse/{warehouseId}")
    public ResponseEntity<List<InventoryResponse>> getByWarehouse(@PathVariable Long warehouseId) {
        return ResponseEntity.ok(inventoryService.findByWarehouse(warehouseId));
    }

    @GetMapping("/check")
    public ResponseEntity<Boolean> checkStock(
            @RequestParam Long productId,
            @RequestParam Long warehouseId,
            @RequestParam int quantity) {
        return ResponseEntity.ok(
                inventoryService.hasEnoughStock(productId, warehouseId, quantity));
    }

    @PostMapping
    public ResponseEntity<InventoryResponse> create(
            @Valid @RequestBody InventoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<InventoryResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody InventoryRequest request) {
        return ResponseEntity.ok(inventoryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        inventoryService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restock")
    public ResponseEntity<InventoryResponse> restock(
            @PathVariable Long id,
            @RequestParam int quantity) {
        return ResponseEntity.ok(inventoryService.restock(id, quantity));
    }

    /** Called by ORDER-SERVICE before confirming an order. */
    @PostMapping("/reserve")
    public ResponseEntity<StockOperationResponse> reserve(
            @Valid @RequestBody StockMovementRequest request) {
        return ResponseEntity.ok(inventoryService.reserve(request));
    }

    /** Compensating call used when an order is cancelled. */
    @PostMapping("/release")
    public ResponseEntity<StockOperationResponse> release(
            @Valid @RequestBody StockMovementRequest request) {
        return ResponseEntity.ok(inventoryService.release(request));
    }
}
