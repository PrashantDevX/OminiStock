package com.warehouse.order.controller;

import com.jayway.jsonpath.JsonPath;
import com.warehouse.order.client.InventoryClient;
import com.warehouse.order.client.ProductClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductClient productClient;

    @MockitoBean
    private InventoryClient inventoryClient;

    private void stubProduct() {
        when(productClient.getProduct(1L))
                .thenReturn(new ProductClient.ProductDto(1L, "ELEC-001",
                        "Wireless Barcode Scanner", 149.99));
    }

    @Test
    void placingOrderReservesStockAndReturnsCreatedOrder() throws Exception {

        stubProduct();
        when(inventoryClient.reserve(any()))
                .thenReturn(new InventoryClient.StockOperationResponse(true, "reserved", 115));

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 1,
                                  "warehouseId": 1,
                                  "quantity": 5,
                                  "customerName": "Riya Sharma",
                                  "customerEmail": "riya@example.com"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.totalAmount").value(749.95))
                .andExpect(jsonPath("$.orderNumber").isNotEmpty());
    }

    @Test
    void placingOrderWithoutStockReturnsConflict() throws Exception {

        stubProduct();
        when(inventoryClient.reserve(any()))
                .thenReturn(new InventoryClient.StockOperationResponse(
                        false, "Insufficient stock", 0));

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 1,
                                  "warehouseId": 1,
                                  "quantity": 999,
                                  "customerName": "Riya Sharma"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void cancellingConfirmedOrderReleasesStock() throws Exception {

        stubProduct();
        when(inventoryClient.reserve(any()))
                .thenReturn(new InventoryClient.StockOperationResponse(true, "reserved", 115));
        when(inventoryClient.release(any()))
                .thenReturn(new InventoryClient.StockOperationResponse(true, "released", 120));

        MvcResult created = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 1,
                                  "warehouseId": 1,
                                  "quantity": 5,
                                  "customerName": "Riya Sharma"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        Integer id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/orders/{id}/cancel", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void validationRejectsMissingCustomerName() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":1,"warehouseId":1,"quantity":2}
                                """))
                .andExpect(status().isBadRequest());
    }
}
