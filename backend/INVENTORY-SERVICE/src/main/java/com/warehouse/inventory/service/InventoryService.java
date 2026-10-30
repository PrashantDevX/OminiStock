package com.warehouse.inventory.service;

import com.warehouse.inventory.client.ProductClient;
import com.warehouse.inventory.dto.InventoryRequest;
import com.warehouse.inventory.dto.InventoryResponse;
import com.warehouse.inventory.dto.StockMovementRequest;
import com.warehouse.inventory.dto.StockOperationResponse;
import com.warehouse.inventory.entity.Inventory;
import com.warehouse.inventory.exception.DependencyUnavailableException;
import com.warehouse.inventory.exception.DuplicateResourceException;
import com.warehouse.inventory.exception.ResourceNotFoundException;
import com.warehouse.inventory.repository.InventoryRepository;
import com.warehouse.inventory.repository.WarehouseRepository;
import feign.FeignException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductClient productClient;

    public InventoryService(InventoryRepository inventoryRepository,
                            WarehouseRepository warehouseRepository,
                            ProductClient productClient) {
        this.inventoryRepository = inventoryRepository;
        this.warehouseRepository = warehouseRepository;
        this.productClient = productClient;
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> findAll() {
        return inventoryRepository.findAll().stream()
                .map(InventoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public InventoryResponse findById(Long id) {
        return InventoryResponse.from(getInventory(id));
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> findByProduct(Long productId) {
        return inventoryRepository.findByProductId(productId).stream()
                .map(InventoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> findByWarehouse(Long warehouseId) {
        return inventoryRepository.findByWarehouseId(warehouseId).stream()
                .map(InventoryResponse::from)
                .toList();
    }

    /** Products at or below their reorder level - used by replenishment alerts. */
    @Transactional(readOnly = true)
    public List<InventoryResponse> findLowStock() {
        return inventoryRepository.findLowStock().stream()
                .map(InventoryResponse::from)
                .toList();
    }

    @Transactional
    public InventoryResponse create(InventoryRequest request) {

        validateProduct(request.productId());
        requireWarehouse(request.warehouseId());

        if (inventoryRepository.existsByProductIdAndWarehouseId(
                request.productId(), request.warehouseId())) {
            throw new DuplicateResourceException(
                    "Stock already exists for product " + request.productId()
                            + " in warehouse " + request.warehouseId());
        }

        Inventory inventory = new Inventory(
                request.productId(),
                request.warehouseId(),
                request.quantity(),
                request.reorderLevel()
        );

        return InventoryResponse.from(inventoryRepository.save(inventory));
    }

    @Transactional
    public InventoryResponse update(Long id, InventoryRequest request) {

        Inventory inventory = getInventory(id);

        boolean keyChanged = !inventory.getProductId().equals(request.productId())
                || !inventory.getWarehouseId().equals(request.warehouseId());

        if (keyChanged) {
            validateProduct(request.productId());
            requireWarehouse(request.warehouseId());

            if (inventoryRepository.existsByProductIdAndWarehouseId(
                    request.productId(), request.warehouseId())) {
                throw new DuplicateResourceException(
                        "Stock already exists for product " + request.productId()
                                + " in warehouse " + request.warehouseId());
            }
        }

        inventory.setProductId(request.productId());
        inventory.setWarehouseId(request.warehouseId());
        inventory.setQuantity(request.quantity());
        inventory.setReorderLevel(request.reorderLevel());

        return InventoryResponse.from(inventoryRepository.save(inventory));
    }

    @Transactional
    public void delete(Long id) {
        inventoryRepository.delete(getInventory(id));
    }

    /** Adds received units to an existing stock record. */
    @Transactional
    public InventoryResponse restock(Long id, int quantity) {

        if (quantity <= 0) {
            throw new IllegalArgumentException("Restock quantity must be positive");
        }

        Inventory inventory = getInventory(id);
        inventory.setQuantity(inventory.getQuantity() + quantity);

        return InventoryResponse.from(inventoryRepository.save(inventory));
    }

    /**
     * Reserves stock for an order. The row is locked for update, so parallel
     * orders cannot both reserve the last unit.
     */
    @Transactional
    public StockOperationResponse reserve(StockMovementRequest request) {

        Inventory inventory = inventoryRepository
                .findForUpdate(request.productId(), request.warehouseId())
                .orElse(null);

        if (inventory == null) {
            return StockOperationResponse.rejected(
                    "No stock record for product " + request.productId()
                            + " in warehouse " + request.warehouseId(), 0);
        }

        int available = inventory.getAvailableQuantity();

        if (available < request.quantity()) {
            return StockOperationResponse.rejected(
                    "Insufficient stock. Requested " + request.quantity()
                            + " but only " + available + " available", available);
        }

        inventory.setReservedQuantity(inventory.getReservedQuantity() + request.quantity());
        Inventory saved = inventoryRepository.save(inventory);

        return StockOperationResponse.ok(
                "Reserved " + request.quantity() + " units", saved.getAvailableQuantity());
    }

    /** Compensating action used when an order is cancelled or fails. */
    @Transactional
    public StockOperationResponse release(StockMovementRequest request) {

        Inventory inventory = inventoryRepository
                .findForUpdate(request.productId(), request.warehouseId())
                .orElse(null);

        if (inventory == null) {
            return StockOperationResponse.rejected("Stock record not found", 0);
        }

        int newReserved = Math.max(0, inventory.getReservedQuantity() - request.quantity());
        inventory.setReservedQuantity(newReserved);

        Inventory saved = inventoryRepository.save(inventory);

        return StockOperationResponse.ok(
                "Released " + request.quantity() + " units", saved.getAvailableQuantity());
    }

    @Transactional(readOnly = true)
    public boolean hasEnoughStock(Long productId, Long warehouseId, int quantity) {
        return inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId)
                .map(inventory -> inventory.getAvailableQuantity() >= quantity)
                .orElse(false);
    }

    public Inventory getInventory(Long id) {
        return inventoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Stock record not found with id " + id));
    }

    private void validateProduct(Long productId) {
        try {
            productClient.getProduct(productId);
        } catch (FeignException.NotFound ex) {
            throw new ResourceNotFoundException("Product not found with id " + productId);
        } catch (FeignException ex) {
            throw new DependencyUnavailableException(
                    "Product service is unavailable. Cannot validate product " + productId, ex);
        }
    }

    private void requireWarehouse(Long warehouseId) {
        if (!warehouseRepository.existsById(warehouseId)) {
            throw new ResourceNotFoundException("Warehouse not found with id " + warehouseId);
        }
    }
}
