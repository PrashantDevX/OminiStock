package com.warehouse.inventory.dto;

import com.warehouse.inventory.entity.Warehouse;

public record WarehouseResponse(
        Long id,
        String code,
        String name,
        String city
) {
    public static WarehouseResponse from(Warehouse warehouse) {
        return new WarehouseResponse(
                warehouse.getId(),
                warehouse.getCode(),
                warehouse.getName(),
                warehouse.getCity()
        );
    }
}
