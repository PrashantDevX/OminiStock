package com.warehouse.inventory.controller;

import com.warehouse.inventory.entity.Inventory;
import com.warehouse.inventory.entity.Warehouse;
import com.warehouse.inventory.repository.InventoryRepository;
import com.warehouse.inventory.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InventoryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    private Long warehouseId;
    private Long stockedInventoryId;

    @BeforeEach
    void createInventoryForTests() {
        Warehouse warehouse = warehouseRepository.save(
                new Warehouse("TEST-WH", "Test Warehouse", "Test City"));
        warehouseId = warehouse.getId();

        Inventory stocked = inventoryRepository.save(
                new Inventory(1L, warehouseId, 120, 20));
        stockedInventoryId = stocked.getId();
        inventoryRepository.save(new Inventory(3L, warehouseId, 8, 10));
    }

    @Test
    void reserveAndReleaseUpdateAvailableStock() throws Exception {

        mockMvc.perform(post("/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":1,"warehouseId":%d,"quantity":5}
                                """.formatted(warehouseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.availableQuantity").value(115));

        mockMvc.perform(post("/inventory/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":1,"warehouseId":%d,"quantity":5}
                                """.formatted(warehouseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.availableQuantity").value(120));
    }

    @Test
    void reserveRejectsOrdersAboveAvailableStock() throws Exception {

        mockMvc.perform(post("/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":3,"warehouseId":%d,"quantity":500}
                                """.formatted(warehouseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Insufficient stock")));
    }

    @Test
    void lowStockEndpointReturnsReplenishmentAlerts() throws Exception {

        mockMvc.perform(get("/inventory/low-stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.lowStock == true)]").exists());
    }

    @Test
    void restockIncreasesPhysicalQuantity() throws Exception {

        mockMvc.perform(post("/inventory/{id}/restock", stockedInventoryId)
                        .param("quantity", "21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(141));
    }

    @Test
    void loadBalancingProbeReturnsServingInstance() throws Exception {

        mockMvc.perform(get("/inventory/instance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("INVENTORY-SERVICE"))
                .andExpect(header().exists("X-Instance-Port"));
    }
}
