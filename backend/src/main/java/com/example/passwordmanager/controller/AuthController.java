package com.example.passwordmanager.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.passwordmanager.service.AuthService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public record RegisterRequest(
            @NotBlank String username,
            @NotBlank String password,
            @NotBlank String masterKey
    ) {}

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {}

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequest request) {
        String token = authService.register(request.username(), request.password(), request.masterKey());
        return ResponseEntity.ok(Map.of("token", token));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@Valid @RequestBody LoginRequest request) {
        try {
            log.info("Login attempt for user: {}", request.username());
            String token = authService.login(request.username(), request.password());
            log.info("User logged in successfully: {}", request.username());
            return ResponseEntity.ok(Map.of("token", token));
            
        } catch (IllegalArgumentException e) {
            log.warn("Login failed: {}", e.getMessage());
            String msg = e.getMessage().toLowerCase();
            
            if (msg.contains("username") || msg.contains("логин") || msg.contains("not found")) {
                return ResponseEntity.status(401).body(Map.of("error", "Неверный логин"));
            } else {
                return ResponseEntity.status(401).body(Map.of("error", "Неверный пароль"));
            }
            
        } catch (Exception e) {
            log.error("Login failed with unexpected error", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Ошибка сервера"));
        }
    }
}