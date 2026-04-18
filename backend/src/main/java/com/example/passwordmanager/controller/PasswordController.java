package com.example.passwordmanager.controller;

import java.util.List;
import java.util.Map;

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

import com.example.passwordmanager.service.PasswordService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api")
@Validated
public class PasswordController {

    private static final Logger log = LoggerFactory.getLogger(PasswordController.class);


    private final PasswordService passwordService;


    public PasswordController(PasswordService passwordService) {
        this.passwordService = passwordService;
    }

    public record UnlockRequest(@NotBlank(message = "Мастер-ключ не может быть пустым") String masterKey) {}

    public record PasswordRequest(
            @NotBlank(message = "Название сервиса обязательно") String service,
            @NotBlank(message = "Логин обязателен") String login,
            @NotBlank(message = "Пароль обязателен") String password
    ) {}

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {

        return ResponseEntity.ok(Map.of("unlocked", false));
    }

    @PostMapping("/unlock")
    public ResponseEntity<Void> unlock(@RequestBody @Valid UnlockRequest request) {
        try {
            log.info("Attempting to unlock vault for key hash: {}", hashForLog(request.masterKey()));
            passwordService.unlock(request.masterKey());
            log.info("Vault unlocked successfully");
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            log.warn("Unlock failed: invalid master key");
            return ResponseEntity.status(401).build();
        } catch (IllegalArgumentException e) {
            log.warn("Unlock failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unlock failed with unexpected error", e);
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/passwords")
    public ResponseEntity<List<Map<String, String>>> getPasswords(
            @RequestHeader("X-Master-Key") String masterKey) {
        try {
            log.debug("Fetching passwords for key hash: {}", hashForLog(masterKey));
            List<Map<String, String>> entries = passwordService.getPasswords(masterKey);
            return ResponseEntity.ok(entries);
        } catch (SecurityException e) {
            log.warn("Get passwords failed: unauthorized");
            return ResponseEntity.status(401).build();
        } catch (Exception e) {
            log.error("Get passwords failed", e);
            return ResponseEntity.status(500).build();
        }
    }

    @PostMapping("/passwords")
    public ResponseEntity<Void> addPassword(
            @RequestHeader("X-Master-Key") String masterKey,
            @RequestBody @Valid PasswordRequest request) {
        try {
            log.info("Adding password for service: {}", request.service());
            passwordService.addPassword(masterKey, request.service(), request.login(), request.password());
            log.info("Password added successfully for service: {}", request.service());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            log.warn("Add password failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (SecurityException e) {
            log.warn("Add password failed: unauthorized");
            return ResponseEntity.status(401).build();
        } catch (Exception e) {
            log.error("Add password failed", e);
            return ResponseEntity.status(500).build();
        }
    }

    @PutMapping("/passwords/{service}")
    public ResponseEntity<Void> updatePassword(
            @RequestHeader("X-Master-Key") String masterKey,
            @PathVariable String service,
            @RequestBody @Valid PasswordRequest request) {
        try {
            if (!service.equals(request.service())) {
                log.warn("Service mismatch: path={}, body={}", service, request.service());
                return ResponseEntity.badRequest().build();
            }
            log.info("Updating password for service: {}", service);
            passwordService.updatePassword(masterKey, request.service(), request.login(), request.password());
            log.info("Password updated successfully for service: {}", service);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            log.warn("Update password failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (SecurityException e) {
            log.warn("Update password failed: unauthorized");
            return ResponseEntity.status(401).build();
        } catch (Exception e) {
            log.error("Update password failed", e);
            return ResponseEntity.status(500).build();
        }
    }

    @DeleteMapping("/passwords/{service}")
    public ResponseEntity<Void> deletePassword(
            @RequestHeader("X-Master-Key") String masterKey,
            @PathVariable String service) {
        try {
            log.info("Deleting password for service: {}", service);
            passwordService.deletePassword(masterKey, service);
            log.info("Password deleted successfully for service: {}", service);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            log.warn("Delete password failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (SecurityException e) {
            log.warn("Delete password failed: unauthorized");
            return ResponseEntity.status(401).build();
        } catch (Exception e) {
            log.error("Delete password failed", e);
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/passwords/{service}/decrypt")
    public ResponseEntity<Map<String, String>> getDecryptedPassword(
            @RequestHeader("X-Master-Key") String masterKey,
            @PathVariable String service) {
        try {
            log.debug("Decrypting entry for service: {}", service);
            Map<String, String> entry = passwordService.getDecryptedEntry(masterKey, service);
            return ResponseEntity.ok(entry);
        } catch (IllegalArgumentException e) {
            log.warn("Decrypt failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (SecurityException e) {
            log.warn("Decrypt failed: unauthorized");
            return ResponseEntity.status(401).build();
        } catch (Exception e) {
            log.error("Decrypt failed", e);
            return ResponseEntity.status(500).build();
        }
    }

    private String hashForLog(String key) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hash).substring(0, 16) + "...";
        } catch (java.security.NoSuchAlgorithmException e) {
            return "hash-error";
        }
    }
}