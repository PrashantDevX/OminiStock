package com.warehouse.gateway.security;

import io.jsonwebtoken.Claims;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Edge security filter:
 *  1. Allows public endpoints (login / register / actuator).
 *  2. Requires a valid, signed JWT everywhere else.
 *  3. Enforces write permissions: only ADMIN and MANAGER can change catalog/stock.
 *  4. Adds trusted X-User-* headers for downstream services.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/auth/login",
            "/auth/register"
    );

    /** These endpoints are only called service-to-service through Eureka. */
    private static final Set<String> INTERNAL_ONLY_PATHS = Set.of(
            "/inventory/reserve",
            "/inventory/release"
    );

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String path = exchange.getRequest().getURI().getPath();
        HttpMethod method = exchange.getRequest().getMethod();

        // CORS pre-flight and public endpoints do not need a token.
        if (HttpMethod.OPTIONS.equals(method) || isPublic(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return writeError(exchange, HttpStatus.UNAUTHORIZED,
                    "Missing Bearer token. Authenticate via POST /auth/login");
        }

        Claims claims;
        try {
            claims = jwtUtil.getClaims(authHeader.substring(7));
        } catch (Exception ex) {
            return writeError(exchange, HttpStatus.UNAUTHORIZED,
                    "Invalid or expired token");
        }

        String role = claims.get("role", String.class);
        String username = claims.getSubject();

        if (INTERNAL_ONLY_PATHS.contains(path)) {
            return writeError(exchange, HttpStatus.FORBIDDEN,
                    "This endpoint is reserved for internal service-to-service calls");
        }

        if (requiresManagerRole(path, method)
                && !"ADMIN".equals(role)
                && !"MANAGER".equals(role)) {
            return writeError(exchange, HttpStatus.FORBIDDEN,
                    "Only ADMIN or MANAGER roles can modify catalog or inventory");
        }

        ServerHttpRequest request = exchange.getRequest().mutate()
                .header("X-User-Name", username == null ? "unknown" : username)
                .header("X-User-Role", role == null ? "STAFF" : role)
                .build();

        return chain.filter(exchange.mutate().request(request).build());
    }

    private boolean isPublic(String path) {
        return PUBLIC_PATHS.contains(path) || path.startsWith("/actuator");
    }

    private boolean requiresManagerRole(String path, HttpMethod method) {

        boolean writeOperation = HttpMethod.POST.equals(method)
                || HttpMethod.PUT.equals(method)
                || HttpMethod.PATCH.equals(method)
                || HttpMethod.DELETE.equals(method);

        if (!writeOperation) {
            return false;
        }

        return path.startsWith("/products")
                || path.startsWith("/inventory")
                || path.startsWith("/warehouses");
    }

    private Mono<Void> writeError(ServerWebExchange exchange,
                                  HttpStatus status,
                                  String message) {

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = """
                {"status":%d,"error":"%s","message":"%s"}
                """.formatted(status.value(), status.getReasonPhrase(), message);

        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory()
                        .wrap(body.getBytes(StandardCharsets.UTF_8)))
        );
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
