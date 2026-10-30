package com.warehouse.product.service;

import com.warehouse.product.dto.ProductRequest;
import com.warehouse.product.dto.ProductResponse;
import com.warehouse.product.entity.Product;
import com.warehouse.product.exception.DuplicateResourceException;
import com.warehouse.product.exception.ResourceNotFoundException;
import com.warehouse.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private ProductRequest request(String sku) {
        return new ProductRequest(sku, "Thermal Printer", "Electronics",
                199.0, "Label printer", true);
    }

    @Test
    void createNormalisesSkuAndPersistsProduct() {

        when(productRepository.existsBySku("ELEC-009")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setId(42L);
            return product;
        });

        ProductResponse response = productService.create(request(" elec-009 "));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());

        assertThat(captor.getValue().getSku()).isEqualTo("ELEC-009");
        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.name()).isEqualTo("Thermal Printer");
    }

    @Test
    void createRejectsDuplicateSku() {

        when(productRepository.existsBySku("ELEC-009")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(request("ELEC-009")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("ELEC-009");

        verify(productRepository, never()).save(any());
    }

    @Test
    void findByIdThrowsWhenProductDoesNotExist() {

        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void updateChangesCatalogFields() {

        Product existing = new Product("ELEC-001", "Old name", "Electronics", 10.0, "old");
        existing.setId(1L);

        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.save(existing)).thenReturn(existing);

        ProductResponse response = productService.update(1L, new ProductRequest(
                "ELEC-001", "New name", "Hardware", 25.0, "new description", false));

        assertThat(response.name()).isEqualTo("New name");
        assertThat(response.category()).isEqualTo("Hardware");
        assertThat(response.price()).isEqualTo(25.0);
        assertThat(response.active()).isFalse();
    }

    @Test
    void deleteRemovesExistingProduct() {

        Product existing = new Product("ELEC-001", "Printer", "Electronics", 10.0, "x");
        existing.setId(1L);

        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));

        productService.delete(1L);

        verify(productRepository).delete(existing);
    }
}
