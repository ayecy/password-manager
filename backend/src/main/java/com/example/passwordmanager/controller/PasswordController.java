package com.example.passwordmanager.controller;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.passwordmanager.service.AuthService;
import com.example.passwordmanager.service.PasswordService;
import com.example.passwordmanager.util.JwtUtil;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api")
@Validated
public class PasswordController {

    private static final Logger log = LoggerFactory.getLogger(PasswordController.class);

    private final PasswordService passwordService;
    private final AuthService authService;
    private final JwtUtil jwtUtil;

    public PasswordController(PasswordService passwordService, AuthService authService, JwtUtil jwtUtil) {
        this.passwordService = Objects.requireNonNull(passwordService);
        this.authService = Objects.requireNonNull(authService);
        this.jwtUtil = Objects.requireNonNull(jwtUtil);
    }

    public record PasswordRequest(
            @NotBlank String service,
            @NotBlank String login,
            @NotBlank String password
    ) {}

    /**
     * Разблокирует хранилище для текущей сессии, если передан мастер-ключ.
     * Вызывается автоматически перед каждой операцией с данными.
     */
    private void ensureVaultUnlocked(String jwt, String masterKey) {
        if (masterKey == null || masterKey.isBlank()) return;

        try {
            String username = jwtUtil.validateAndGetSubject(jwt);
            String salt = authService.getMasterKeySalt(username);
            passwordService.unlockForUser(jwt, masterKey, salt);
        } catch (Exception e) {
            log.warn("Failed to unlock vault for session: {}", jwt.substring(0, 10));
        }
    }

    @GetMapping("/passwords")
    public ResponseEntity<List<Map<String, String>>> getPasswords(
            @RequestHeader("Authorization") String authHeader,
            @RequestHeader(value = "X-Master-Key", required = false) String masterKey) {
        String jwt = authHeader.replace("Bearer ", "");
        ensureVaultUnlocked(jwt, masterKey);

        try {
            return ResponseEntity.ok(passwordService.getPasswords(jwt));
        } catch (SecurityException e) {
            return ResponseEntity.status(401).build();
        } catch (Exception e) {
            log.error("Failed to get passwords", e);
            return ResponseEntity.status(500).build();
        }
    }

    @PostMapping("/passwords")
    public ResponseEntity<Void> addPassword(
            @RequestHeader("Authorization") String authHeader,
            @RequestHeader(value = "X-Master-Key", required = false) String masterKey,
            @RequestBody @Valid PasswordRequest request) {
        String jwt = authHeader.replace("Bearer ", "");
        ensureVaultUnlocked(jwt, masterKey);

        try {
            passwordService.addPassword(jwt, request.service(), request.login(), request.password());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            log.warn("Add password failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(401).build();
        } catch (Exception e) {
            log.error("Failed to add password", e);
            return ResponseEntity.status(500).build();
        }
    }

    @PutMapping("/passwords/{service}")
    public ResponseEntity<Map<String, String>> updatePassword(
            @RequestHeader("Authorization") String authHeader,
            @RequestHeader(value = "X-Master-Key", required = false) String masterKey,
            @PathVariable String service,  // ← оригинальное имя (из пути)
            @RequestBody @Valid PasswordRequest request) {  // ← request.service() может быть новым именем
        
        String jwt = authHeader.replace("Bearer ", "");
        ensureVaultUnlocked(jwt, masterKey);

        try {
            passwordService.updatePassword(jwt, service, request.service(), request.login(), request.password());
            log.info("Password updated: {} → {}", service, request.service());
            return ResponseEntity.ok().build();
            
        } catch (IllegalArgumentException e) {
            log.warn("Update failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
            
        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Сессия истекла"));
            
        } catch (Exception e) {
            log.error("Update failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Ошибка сервера"));
        }
    }

    @DeleteMapping("/passwords/{service}")
    public ResponseEntity<Map<String, String>> deletePassword(
            @RequestHeader("Authorization") String authHeader,
            @RequestHeader(value = "X-Master-Key", required = false) String masterKey,
            @PathVariable String service) {
        
        String jwt = authHeader.replace("Bearer ", "");
        ensureVaultUnlocked(jwt, masterKey);

        try {
            log.info("Deleting password for service: {}", service);
            passwordService.deletePassword(jwt, service);
            log.info("Password deleted successfully for service: {}", service);
            return ResponseEntity.ok().build();
            
        } catch (IllegalArgumentException e) {
            log.warn("Delete password failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
            
        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Сессия истекла"));
            
        } catch (Exception e) {
            log.error("Delete password failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Ошибка сервера"));
        }
    }
}