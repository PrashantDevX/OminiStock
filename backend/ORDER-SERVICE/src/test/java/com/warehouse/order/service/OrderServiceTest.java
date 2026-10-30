package com.warehouse.order.service;

import com.warehouse.order.client.InventoryClient;
import com.warehouse.order.client.ProductClient;
import com.warehouse.order.dto.OrderRequest;
import com.warehouse.order.dto.OrderResponse;
import com.warehouse.order.entity.Order;
import com.warehouse.order.entity.OrderStatus;
import com.warehouse.order.exception.InvalidOrderStateException;
import com.warehouse.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductClient productClient;

    @Mock
    private InventoryClient inventoryClient;

    @InjectMocks
    private OrderService orderService;

    private OrderRequest request() {
        return new OrderRequest(1L, 1L, 2, "Riya Sharma", "riya@example.com");
    }

    private ProductClient.ProductDto product() {
        return new ProductClient.ProductDto(1L, "ELEC-001", "Barcode Scanner", 149.99);
    }

    private void stubSave() {
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(100L);
            return order;
        });
    }

    @Test
    void placeOrderConfirmsAndCalculatesTotalWhenStockIsReserved() {

        when(orderRepository.existsByOrderNumber(anyString())).thenReturn(false);
        when(productClient.getProduct(1L)).thenReturn(product());
        when(inventoryClient.reserve(any()))
                .thenReturn(new InventoryClient.StockOperationResponse(true, "reserved", 118));
        stubSave();

        OrderResponse response = orderService.placeOrder(request());

        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.productName()).isEqualTo("Barcode Scanner");
        assertThat(response.unitPrice()).isEqualTo(149.99);
        assertThat(response.totalAmount()).isEqualTo(299.98);
        assertThat(response.orderNumber()).startsWith("ORD-");

        verify(inventoryClient).reserve(any(InventoryClient.StockMovementRequest.class));
        verify(inventoryClient, never()).release(any());
    }

    @Test
    void placeOrderIsRejectedWhenInventoryCannotReserveStock() {

        when(orderRepository.existsByOrderNumber(anyString())).thenReturn(false);
        when(productClient.getProduct(1L)).thenReturn(product());
        when(inventoryClient.reserve(any()))
                .thenReturn(new InventoryClient.StockOperationResponse(false, "Insufficient stock", 0));
        stubSave();

        OrderResponse response = orderService.placeOrder(request());

        assertThat(response.status()).isEqualTo("REJECTED");
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void cancelReleasesReservedStockAndMarksOrderCancelled() {

        Order order = new Order();
        order.setId(5L);
        order.setOrderNumber("ORD-1");
        order.setProductId(1L);
        order.setWarehouseId(1L);
        order.setQuantity(2);
        order.setStatus(OrderStatus.CONFIRMED);

        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(inventoryClient.release(any()))
                .thenReturn(new InventoryClient.StockOperationResponse(true, "released", 120));
        when(orderRepository.save(order)).thenReturn(order);

        OrderResponse response = orderService.cancel(5L);

        assertThat(response.status()).isEqualTo("CANCELLED");
        verify(inventoryClient).release(any(InventoryClient.StockMovementRequest.class));
    }

    @Test
    void cancelRejectsOrdersThatWereNeverConfirmed() {

        Order order = new Order();
        order.setId(6L);
        order.setStatus(OrderStatus.REJECTED);

        when(orderRepository.findById(6L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancel(6L))
                .isInstanceOf(InvalidOrderStateException.class);

        verify(inventoryClient, never()).release(any());
    }
}
