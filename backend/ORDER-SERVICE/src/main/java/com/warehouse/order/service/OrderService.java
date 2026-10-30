package com.warehouse.order.service;

import com.warehouse.order.client.InventoryClient;
import com.warehouse.order.client.ProductClient;
import com.warehouse.order.dto.OrderRequest;
import com.warehouse.order.dto.OrderResponse;
import com.warehouse.order.dto.OrderSummary;
import com.warehouse.order.entity.Order;
import com.warehouse.order.entity.OrderStatus;
import com.warehouse.order.exception.DependencyUnavailableException;
import com.warehouse.order.exception.InvalidOrderStateException;
import com.warehouse.order.exception.ResourceNotFoundException;
import com.warehouse.order.repository.OrderRepository;
import feign.FeignException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OrderService {

    private static final DateTimeFormatter ORDER_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;

    public OrderService(OrderRepository orderRepository,
                        ProductClient productClient,
                        InventoryClient inventoryClient) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
        this.inventoryClient = inventoryClient;
    }

    /**
     * Order -> Inventory -> Product saga:
     *  1. Read product details (name / price) from PRODUCT-SERVICE.
     *  2. Ask INVENTORY-SERVICE to atomically reserve the requested units.
     *  3. Persist a CONFIRMED order, or a REJECTED order for audit purposes.
     *  4. If persistence fails after reservation, release the stock (compensation).
     */
    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {

        ProductClient.ProductDto product = fetchProduct(request.productId());

        Order order = new Order();
        order.setOrderNumber(nextOrderNumber());
        order.setCustomerName(request.customerName().trim());
        order.setCustomerEmail(request.customerEmail());
        order.setProductId(product.id());
        order.setProductName(product.name());
        order.setWarehouseId(request.warehouseId());
        order.setQuantity(request.quantity());
        order.setUnitPrice(product.price());
        order.setTotalAmount(round(product.price() * request.quantity()));

        InventoryClient.StockOperationResponse reservation = reserveStock(request);

        if (!reservation.success()) {
            order.setStatus(OrderStatus.REJECTED);
            return OrderResponse.from(orderRepository.save(order));
        }

        order.setStatus(OrderStatus.CONFIRMED);

        try {
            return OrderResponse.from(orderRepository.save(order));
        } catch (RuntimeException ex) {
            releaseStock(request);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findAll(String status) {

        List<Order> orders;

        if (status == null || status.isBlank()) {
            orders = orderRepository.findAll();
        } else {
            try {
                orders = orderRepository.findByStatus(
                        OrderStatus.valueOf(status.trim().toUpperCase()));
            } catch (IllegalArgumentException ex) {
                throw new InvalidOrderStateException(
                        "Unknown order status: " + status);
            }
        }

        return orders.stream().map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(Long id) {
        return OrderResponse.from(getOrder(id));
    }

    /**
     * Cancels a confirmed order and releases its reservation.
     * This is the compensating transaction of the reservation step.
     */
    @Transactional
    public OrderResponse cancel(Long id) {

        Order order = getOrder(id);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            return OrderResponse.from(order);
        }

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new InvalidOrderStateException(
                    "Only CONFIRMED orders can be cancelled. Current status: "
                            + order.getStatus());
        }

        InventoryClient.StockOperationResponse release;
        try {
            release = inventoryClient.release(new InventoryClient.StockMovementRequest(
                    order.getProductId(),
                    order.getWarehouseId(),
                    order.getQuantity()
            ));
        } catch (FeignException ex) {
            throw new DependencyUnavailableException(
                    "Inventory service is unavailable. Order was not cancelled", ex);
        }

        if (!release.success()) {
            throw new DependencyUnavailableException(
                    "Stock could not be released: " + release.message(), null);
        }

        order.setStatus(OrderStatus.CANCELLED);
        return OrderResponse.from(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public OrderSummary summary() {

        List<Order> orders = orderRepository.findAll();

        long confirmed = orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.CONFIRMED).count();
        long rejected = orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.REJECTED).count();
        long cancelled = orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.CANCELLED).count();

        double revenue = orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.CONFIRMED)
                .mapToDouble(Order::getTotalAmount)
                .sum();

        return new OrderSummary(
                orders.size(),
                confirmed,
                rejected,
                cancelled,
                round(revenue)
        );
    }

    private ProductClient.ProductDto fetchProduct(Long productId) {
        try {
            return productClient.getProduct(productId);
        } catch (FeignException.NotFound ex) {
            throw new ResourceNotFoundException("Product not found with id " + productId);
        } catch (FeignException ex) {
            throw new DependencyUnavailableException(
                    "Product service is unavailable. Cannot place the order", ex);
        }
    }

    private InventoryClient.StockOperationResponse reserveStock(OrderRequest request) {
        try {
            return inventoryClient.reserve(new InventoryClient.StockMovementRequest(
                    request.productId(),
                    request.warehouseId(),
                    request.quantity()
            ));
        } catch (FeignException ex) {
            throw new DependencyUnavailableException(
                    "Inventory service is unavailable. Cannot reserve stock", ex);
        }
    }

    private void releaseStock(OrderRequest request) {
        try {
            inventoryClient.release(new InventoryClient.StockMovementRequest(
                    request.productId(),
                    request.warehouseId(),
                    request.quantity()
            ));
        } catch (FeignException ignored) {
            // Best effort compensation - the inconsistent reservation is logged
            // by the inventory service and can be reconciled manually.
        }
    }

    private Order getOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found with id " + id));
    }

    private String nextOrderNumber() {
        String candidate;
        do {
            candidate = "ORD-" + LocalDateTime.now().format(ORDER_TIME)
                    + "-" + ThreadLocalRandom.current().nextInt(1000, 9999);
        } while (orderRepository.existsByOrderNumber(candidate));
        return candidate;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
