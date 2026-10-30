package com.warehouse.inventory.service;

import com.warehouse.inventory.dto.WarehouseRequest;
import com.warehouse.inventory.dto.WarehouseResponse;
import com.warehouse.inventory.entity.Warehouse;
import com.warehouse.inventory.exception.DuplicateResourceException;
import com.warehouse.inventory.exception.ResourceNotFoundException;
import com.warehouse.inventory.repository.InventoryRepository;
import com.warehouse.inventory.repository.WarehouseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final InventoryRepository inventoryRepository;

    public WarehouseService(WarehouseRepository warehouseRepository,
                            InventoryRepository inventoryRepository) {
        this.warehouseRepository = warehouseRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> findAll() {
        return warehouseRepository.findAll().stream()
                .map(WarehouseResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public WarehouseResponse findById(Long id) {
        return WarehouseResponse.from(getWarehouse(id));
    }

    @Transactional
    public WarehouseResponse create(WarehouseRequest request) {

        String code = request.code().trim().toUpperCase();

        if (warehouseRepository.existsByCode(code)) {
            throw new DuplicateResourceException("Warehouse code " + code + " already exists");
        }

        Warehouse warehouse = new Warehouse(code, request.name().trim(), request.city());
        return WarehouseResponse.from(warehouseRepository.save(warehouse));
    }

    @Transactional
    public WarehouseResponse update(Long id, WarehouseRequest request) {

        Warehouse warehouse = getWarehouse(id);
        String code = request.code().trim().toUpperCase();

        if (!warehouse.getCode().equals(code) && warehouseRepository.existsByCode(code)) {
            throw new DuplicateResourceException("Warehouse code " + code + " already exists");
        }

        warehouse.setCode(code);
        warehouse.setName(request.name().trim());
        warehouse.setCity(request.city());

        return WarehouseResponse.from(warehouseRepository.save(warehouse));
    }

    @Transactional
    public void delete(Long id) {

        getWarehouse(id);

        if (inventoryRepository.existsByWarehouseId(id)) {
            throw new DuplicateResourceException(
                    "Warehouse still holds stock and cannot be deleted");
        }

        warehouseRepository.deleteById(id);
    }

    private Warehouse getWarehouse(Long id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Warehouse not found with id " + id));
    }
}
