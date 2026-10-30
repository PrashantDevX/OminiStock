package com.warehouse.product.service;

import com.warehouse.product.dto.ProductRequest;
import com.warehouse.product.dto.ProductResponse;
import com.warehouse.product.entity.Product;
import com.warehouse.product.exception.DuplicateResourceException;
import com.warehouse.product.exception.ResourceNotFoundException;
import com.warehouse.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {

        String sku = normaliseSku(request.sku());

        if (productRepository.existsBySku(sku)) {
            throw new DuplicateResourceException("A product with SKU " + sku + " already exists");
        }

        Product product = new Product(
                sku,
                request.name().trim(),
                request.category().trim(),
                request.price(),
                request.description()
        );
        product.setActive(request.active() == null || request.active());

        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll(String search) {

        List<Product> products;

        if (search == null || search.isBlank()) {
            products = productRepository.findAll();
        } else {
            String term = search.trim();
            products = productRepository
                    .findByNameContainingIgnoreCaseOrSkuContainingIgnoreCaseOrCategoryContainingIgnoreCase(
                            term, term, term);
        }

        return products.stream().map(ProductResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        return ProductResponse.from(getProduct(id));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {

        Product product = getProduct(id);
        String sku = normaliseSku(request.sku());

        if (!product.getSku().equals(sku) && productRepository.existsBySku(sku)) {
            throw new DuplicateResourceException("A product with SKU " + sku + " already exists");
        }

        product.setSku(sku);
        product.setName(request.name().trim());
        product.setCategory(request.category().trim());
        product.setPrice(request.price());
        product.setDescription(request.description());

        if (request.active() != null) {
            product.setActive(request.active());
        }

        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public void delete(Long id) {

        Product product = getProduct(id);
        productRepository.delete(product);
    }

    private Product getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id " + id));
    }

    private String normaliseSku(String sku) {
        return sku.trim().toUpperCase();
    }
}
