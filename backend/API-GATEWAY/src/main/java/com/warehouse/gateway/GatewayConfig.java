package com.warehouse.gateway;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Centralised routing table. Every URI uses the "lb://" scheme so that
 * Spring Cloud LoadBalancer resolves healthy instances from Eureka.
 */
@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("auth-service", r -> r
                        .path("/auth/**")
                        .uri("lb://AUTH-SERVICE"))
                .route("product-service", r -> r
                        .path("/products/**")
                        .uri("lb://PRODUCT-SERVICE"))
                .route("inventory-service", r -> r
                        .path("/inventory/**")
                        .uri("lb://INVENTORY-SERVICE"))
                .route("warehouse-service", r -> r
                        .path("/warehouses/**")
                        .uri("lb://INVENTORY-SERVICE"))
                .route("order-service", r -> r
                        .path("/orders/**")
                        .uri("lb://ORDER-SERVICE"))
                .build();
    }

    @Bean
    public CorsWebFilter corsWebFilter() {

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "http://localhost:3000"
        ));
        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-Instance-Port"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}
