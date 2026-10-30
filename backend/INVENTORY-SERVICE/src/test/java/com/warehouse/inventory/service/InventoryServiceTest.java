package com.warehouse.inventory.service;

import com.warehouse.inventory.client.ProductClient;
import com.warehouse.inventory.dto.InventoryRequest;
import com.warehouse.inventory.dto.StockMovementRequest;
import com.warehouse.inventory.dto.StockOperationResponse;
import com.warehouse.inventory.entity.Inventory;
import com.warehouse.inventory.exception.DuplicateResourceException;
import com.warehouse.inventory.exception.ResourceNotFoundException;
import com.warehouse.inventory.repository.InventoryRepository;
import com.warehouse.inventory.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private ProductClient productClient;

    @InjectMocks
    private InventoryService inventoryService;

    private Inventory stock(int quantity, int reserved, int reorderLevel) {
        Inventory inventory = new Inventory(1L, 1L, quantity, reorderLevel);
        inventory.setId(10L);
        inventory.setReservedQuantity(reserved);
        return inventory;
    }

    @Test
    void createValidatesProductAndWarehouseBeforeSaving() {

        when(productClient.getProduct(1L))
                .thenReturn(new ProductClient.ProductDto(1L, "ELEC-001", "Scanner", 149.99));
        when(warehouseRepository.existsById(1L)).thenReturn(true);
        when(inventoryRepository.existsByProductIdAndWarehouseId(1L, 1L)).thenReturn(false);
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> {
            Inventory saved = invocation.getArgument(0);
            saved.setId(55L);
            return saved;
        });

        var response = inventoryService.create(
                new InventoryRequest(1L, 1L, 20, 5));

        assertThat(response.id()).isEqualTo(55L);
        assertThat(response.quantity()).isEqualTo(20);
        assertThat(response.availableQuantity()).isEqualTo(20);
        verify(productClient).getProduct(1L);
    }

    @Test
    void createRejectsDuplicateStockRecord() {

        when(productClient.getProduct(1L))
                .thenReturn(new ProductClient.ProductDto(1L, "ELEC-001", "Scanner", 149.99));
        when(warehouseRepository.existsById(1L)).thenReturn(true);
        when(inventoryRepository.existsByProductIdAndWarehouseId(1L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> inventoryService.create(new InventoryRequest(1L, 1L, 5, 2)))
                .isInstanceOf(DuplicateResourceException.class);

        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void createRejectsUnknownWarehouse() {

        when(productClient.getProduct(1L))
                .thenReturn(new ProductClient.ProductDto(1L, "ELEC-001", "Scanner", 149.99));
        when(warehouseRepository.existsById(9L)).thenReturn(false);

        assertThatThrownBy(() -> inventoryService.create(new InventoryRequest(1L, 9L, 5, 2)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Warehouse");
    }

    @Test
    void reserveReducesAvailableQuantity() {

        Inventory inventory = stock(10, 0, 5);

        when(inventoryRepository.findForUpdate(1L, 1L)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(inventory)).thenReturn(inventory);

        StockOperationResponse response = inventoryService.reserve(
                new StockMovementRequest(1L, 1L, 4));

        assertThat(response.success()).isTrue();
        assertThat(response.availableQuantity()).isEqualTo(6);
        assertThat(inventory.getReservedQuantity()).isEqualTo(4);
    }

    @Test
    void reserveFailsWhenAvailableStockIsTooLow() {

        Inventory inventory = stock(2, 1, 0);

        when(inventoryRepository.findForUpdate(1L, 1L)).thenReturn(Optional.of(inventory));

        StockOperationResponse response = inventoryService.reserve(
                new StockMovementRequest(1L, 1L, 5));

        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("Insufficient stock");
        assertThat(inventory.getReservedQuantity()).isEqualTo(1);
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void releaseRestoresReservedUnits() {

        Inventory inventory = stock(10, 6, 2);

        when(inventoryRepository.findForUpdate(1L, 1L)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(inventory)).thenReturn(inventory);

        StockOperationResponse response = inventoryService.release(
                new StockMovementRequest(1L, 1L, 4));

        assertThat(response.success()).isTrue();
        assertThat(inventory.getReservedQuantity()).isEqualTo(2);
        assertThat(response.availableQuantity()).isEqualTo(8);
    }

    @Test
    void releaseNeverGoesBelowZero() {

        Inventory inventory = stock(10, 1, 2);

        when(inventoryRepository.findForUpdate(1L, 1L)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(inventory)).thenReturn(inventory);

        inventoryService.release(new StockMovementRequest(1L, 1L, 5));

        assertThat(inventory.getReservedQuantity()).isZero();
    }
}
