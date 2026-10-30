package com.warehouse.auth.controller;

import com.warehouse.auth.dto.AuthResponse;
import com.warehouse.auth.dto.LoginRequest;
import com.warehouse.auth.dto.RegisterRequest;
import com.warehouse.auth.dto.UserResponse;
import com.warehouse.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Public endpoint - issues a JWT when credentials are valid. */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** Public endpoint - creates a STAFF account and returns a JWT. */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /** Returns the profile of the user represented by the Bearer token. */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ResponseEntity.ok(authService.currentUser(authorization));
    }
}
