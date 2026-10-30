package com.warehouse.order.controller;

import com.warehouse.order.dto.OrderRequest;
import com.warehouse.order.dto.OrderResponse;
import com.warehouse.order.dto.OrderSummary;
import com.warehouse.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(
            @Valid @RequestBody OrderRequest request) {

        OrderResponse order = orderService.placeOrder(request);

        if ("REJECTED".equals(order.status())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(order);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAll(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(orderService.findAll(status));
    }

    @GetMapping("/summary")
    public ResponseEntity<OrderSummary> summary() {
        return ResponseEntity.ok(orderService.summary());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.findById(id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.cancel(id));
    }
}
