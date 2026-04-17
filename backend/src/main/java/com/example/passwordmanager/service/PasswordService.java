package com.example.passwordmanager.service;

import com.example.passwordmanager.model.PasswordEntity;
import com.example.passwordmanager.repository.JsonPasswordRepository; // ← Новый репозиторий для JSON
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PreDestroy;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class PasswordService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 16;        
    private static final int GCM_IV_LENGTH = 12;         
    private static final int PBKDF2_ITERATIONS = 100_000; 
    private static final int KEY_LENGTH_BITS = 256;      
    private static final long SESSION_TIMEOUT_MINUTES = 15; 


    private static final Logger log = LoggerFactory.getLogger(PasswordService.class);


    private final JsonPasswordRepository passwordRepository; 


    private final Map<String, CachedKey> keys = new ConcurrentHashMap<>();


    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();


    private static class CachedKey {
        final SecretKey key;           
        volatile long lastAccess;      

        CachedKey(SecretKey key) {
            this.key = key;
            this.lastAccess = System.currentTimeMillis();
        }
    }


    public PasswordService(JsonPasswordRepository passwordRepository) {
        this.passwordRepository = Objects.requireNonNull(passwordRepository, "Repository cannot be null");
        scheduler.scheduleAtFixedRate(this::cleanupExpiredKeys, 1, 1, TimeUnit.MINUTES);
        log.info("PasswordService initialized with session timeout: {} minutes", SESSION_TIMEOUT_MINUTES);
    }

    /**
     * @param masterKey мастер-ключ, введённый пользователем
     */
    public void unlock(String masterKey) {
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalArgumentException("Мастер-ключ не может быть пустым");
        }
        String keyHash = hashMasterKey(masterKey);
        keys.compute(keyHash, (k, cached) -> {
            if (cached != null) {
                // Ключ уже есть — просто обновляем время доступа
                cached.lastAccess = System.currentTimeMillis();
                log.debug("Session refreshed for key hash: {}", truncateHash(keyHash));
                return cached;
            }
            // Деривируем новый ключ
            log.info("Deriving new key for hash: {}", truncateHash(keyHash));
            return new CachedKey(deriveKey(masterKey));
        });
    }

    /**
     * @param masterKey мастер-ключ пользователя
     * @return действующий SecretKey
     * @throws SecurityException если ключ не найден или истёк
     */
    private SecretKey getKey(String masterKey) {
        String keyHash = hashMasterKey(masterKey);
        CachedKey cached = keys.get(keyHash);
        if (cached == null) {
            log.warn("Key not found for hash: {}", truncateHash(keyHash));
            throw new SecurityException("Хранилище не разблокировано");
        }

        cached.lastAccess = System.currentTimeMillis();
        return cached.key;
    }


    public List<Map<String, String>> getPasswords(String masterKey) throws Exception {
        SecretKey key = getKey(masterKey);
        List<PasswordEntity> entities = passwordRepository.findAll();
        List<Map<String, String>> result = new ArrayList<>(entities.size());

        for (PasswordEntity e : entities) {
            result.add(Map.of(
                    "service", e.getService(),
                    "login", decrypt(e.getEncryptedLogin(), key),
                    "password", decrypt(e.getEncryptedPassword(), key)
            ));
        }
        log.debug("Returned {} password entries", result.size());
        return result;
    }


    public Map<String, String> getDecryptedEntry(String masterKey, String service) throws Exception {
        SecretKey key = getKey(masterKey);
        PasswordEntity entity = passwordRepository.findByService(service)
                .orElseThrow(() -> {
                    log.warn("Entry not found for service: {}", service);
                    return new IllegalArgumentException("Запись не найдена");
                });

        return Map.of(
                "service", service,
                "login", decrypt(entity.getEncryptedLogin(), key),
                "password", decrypt(entity.getEncryptedPassword(), key)
        );
    }


    @Transactional
    public void addPassword(String masterKey, String service, String login, String password) throws Exception {
        validateInput(service, login, password);
        SecretKey key = getKey(masterKey);

        if (passwordRepository.existsByService(service)) {
            log.warn("Duplicate add attempt for service: {}", service);
            throw new IllegalArgumentException("Запись для этого сервиса уже существует");
        }

        PasswordEntity entity = new PasswordEntity(
                service,
                encrypt(login, key),
                encrypt(password, key)
        );
        passwordRepository.save(entity);
        log.info("Added password entry for service: {}", service);
    }


    @Transactional
    public void updatePassword(String masterKey, String service, String login, String password) throws Exception {
        validateInput(service, login, password);
        SecretKey key = getKey(masterKey);

        PasswordEntity entity = passwordRepository.findByService(service)
                .orElseThrow(() -> {
                    log.warn("Update failed: entry not found for service: {}", service);
                    return new IllegalArgumentException("Запись не найдена");
                });

        entity.setEncryptedLogin(encrypt(login, key));
        entity.setEncryptedPassword(encrypt(password, key));
        passwordRepository.save(entity);
        log.info("Updated password entry for service: {}", service);
    }


    @Transactional
    public void deletePassword(String masterKey, String service) {
        if (service == null || service.isBlank()) {
            throw new IllegalArgumentException("Service name cannot be empty");
        }
        if (!passwordRepository.existsByService(service)) {
            log.warn("Delete failed: entry not found for service: {}", service);
            throw new IllegalArgumentException("Запись не найдена");
        }
        passwordRepository.deleteByService(service);
        log.info("Deleted password entry for service: {}", service);
    }


    /**
     * Деривация ключа через PBKDF2 с фиксированной солью.
     */
    private SecretKey deriveKey(String masterKey) {
        byte[] salt = "password-manager-fixed-salt-v1".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        PKCS5S2ParametersGenerator gen = new PKCS5S2ParametersGenerator(new SHA256Digest());
        gen.init(
                masterKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                salt,
                PBKDF2_ITERATIONS
        );
        byte[] keyBytes = ((KeyParameter) gen.generateDerivedParameters(KEY_LENGTH_BITS)).getKey();
        log.debug("Key derived successfully using PBKDF2 with {} iterations", PBKDF2_ITERATIONS);
        return new SecretKeySpec(keyBytes, "AES");
    }


    private String hashMasterKey(String masterKey) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(masterKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            log.error("Failed to hash master key", e);
            throw new RuntimeException("Key hashing failed", e);
        }
    }

    /**
     * Шифрование строки через AES/GCM.
     * Формат результата: IV (12 байт) + ciphertext + auth tag (16 байт) -> Base64.
     */
    private String encrypt(String plaintext, SecretKey key) throws Exception {
        if (plaintext == null) {
            throw new IllegalArgumentException("Cannot encrypt null value");
        }
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv); // Криптографически стойкий генератор

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec ivSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);

        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] result = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);

        return Base64.getEncoder().encodeToString(result);
    }

    /**
     * Дешифровка строки из формата: Base64(IV + ciphertext + tag).
     */
    private String decrypt(String encryptedData, SecretKey key) throws Exception {
        if (encryptedData == null || encryptedData.isEmpty()) {
            throw new IllegalArgumentException("Cannot decrypt empty value");
        }
        byte[] data = Base64.getDecoder().decode(encryptedData);
        if (data.length < GCM_IV_LENGTH + GCM_TAG_LENGTH) {
            throw new IllegalArgumentException("Invalid encrypted data format");
        }

        byte[] iv = Arrays.copyOfRange(data, 0, GCM_IV_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(data, GCM_IV_LENGTH, data.length);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec ivSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, ivSpec);

        byte[] plaintext = cipher.doFinal(ciphertext);
        return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
    }


    private void validateInput(String service, String login, String password) {
        if (service == null || service.isBlank()) {
            throw new IllegalArgumentException("Service name cannot be empty");
        }
        if (login == null || login.isBlank()) {
            throw new IllegalArgumentException("Login cannot be empty");
        }
        if (password == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
    }


    private void cleanupExpiredKeys() {
        long now = System.currentTimeMillis();
        long timeoutMillis = SESSION_TIMEOUT_MINUTES * 60_000L;
        int removed = 0;

        Iterator<Map.Entry<String, CachedKey>> iterator = keys.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CachedKey> entry = iterator.next();
            if (now - entry.getValue().lastAccess > timeoutMillis) {
                iterator.remove();
                removed++;
                log.debug("Expired session removed for hash: {}", truncateHash(entry.getKey()));
            }
        }
        if (removed > 0) {
            log.info("Cleaned up {} expired sessions", removed);
        }
    }


    private String truncateHash(String hash) {
        if (hash == null || hash.length() <= 16) return hash;
        return hash.substring(0, 16) + "...";
    }


    @PreDestroy
    public void shutdown() {
        log.info("Shutting down PasswordService: clearing {} cached keys", keys.size());
        keys.clear();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}