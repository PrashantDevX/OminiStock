package com.warehouse.product.controller;

import com.jayway.jsonpath.JsonPath;
import com.warehouse.product.entity.Product;
import com.warehouse.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end slice over the real controller, service, repository and H2 database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void fullCrudLifecycle() throws Exception {

        MvcResult created = mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sku": "TEST-100",
                                  "name": "Integration Test Product",
                                  "category": "Testing",
                                  "price": 99.5,
                                  "description": "Created by an integration test"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.sku").value("TEST-100"))
                .andReturn();

        Integer id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Integration Test Product"));

        mockMvc.perform(put("/products/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sku": "TEST-100",
                                  "name": "Updated Product",
                                  "category": "Testing",
                                  "price": 120.0,
                                  "description": "Updated",
                                  "active": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Product"))
                .andExpect(jsonPath("$.price").value(120.0));

        mockMvc.perform(delete("/products/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/products/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchFiltersByCategory() throws Exception {
        productRepository.save(new Product(
                "TEST-PACK-100",
                "Integration Test Package",
                "Packaging",
                10.0,
                "Test fixture"
        ));

        mockMvc.perform(get("/products").param("search", "Packaging"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("Packaging"));
    }

    @Test
    void createRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"","name":"","category":"","price":-5}
                                """))
                .andExpect(status().isBadRequest());
    }
}
