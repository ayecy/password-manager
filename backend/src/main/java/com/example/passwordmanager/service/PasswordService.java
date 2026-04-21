package com.example.passwordmanager.service;

import com.example.passwordmanager.model.PasswordEntity;
import com.example.passwordmanager.repository.JsonPasswordRepository;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.bouncycastle.crypto.params.KeyParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
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
public final class PasswordService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 16;
    private static final int GCM_IV_LENGTH = 12;
    private static final int PBKDF2_ITERATIONS = 100_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final long SESSION_TIMEOUT_MINUTES = 15;

    private static final Logger log = LoggerFactory.getLogger(PasswordService.class);

    private final JsonPasswordRepository passwordRepository;

    private final Map<String, CachedKey> vaultKeys = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private static final class CachedKey {
        private final SecretKey key;
        private volatile long lastAccess;

        private CachedKey(SecretKey key) {
            this.key = Objects.requireNonNull(key);
            this.lastAccess = System.currentTimeMillis();
        }

        private SecretKey getKey() { return key; }
        private void refresh() { this.lastAccess = System.currentTimeMillis(); }
        private long getLastAccess() { return lastAccess; }
    }

    public PasswordService(JsonPasswordRepository passwordRepository) {
        this.passwordRepository = Objects.requireNonNull(passwordRepository, "Repository cannot be null");
        scheduler.scheduleAtFixedRate(this::cleanupExpiredKeys, 1, 1, TimeUnit.MINUTES);
        log.info("PasswordService initialized: session timeout = {} min, PBKDF2 iterations = {}",
                SESSION_TIMEOUT_MINUTES, PBKDF2_ITERATIONS);
    }

    // =================================================================
    // ======= ПУБЛИЧНЫЕ МЕТОДЫ (интерфейс для контроллера) ============
    // =================================================================

    public void unlock(String masterKey) {
        validateMasterKey(masterKey);
        String sessionKey = hashMasterKey(masterKey);
        vaultKeys.compute(sessionKey, (k, cached) -> {
            if (cached != null) { cached.refresh(); return cached; }
            return new CachedKey(deriveKeyLegacy(masterKey));
        });
        log.debug("Vault unlocked (legacy mode) for session: {}", truncateHash(sessionKey));
    }

    /**
     * @param jwt JWT-токен аутентифицированного пользователя
     * @param masterKey мастер-ключ, введённый пользователем
     * @param userSalt соль пользователя (из БД/репозитория)
     */
    public void unlockForUser(String jwt, String masterKey, String userSalt) {
        if (jwt == null || jwt.isBlank()) throw new IllegalArgumentException("JWT required");
        validateMasterKey(masterKey);
        if (userSalt == null || userSalt.isBlank()) throw new IllegalArgumentException("User salt required");


        vaultKeys.compute(jwt, (token, cached) -> {
            if (cached != null) { cached.refresh(); return cached; }
            byte[] saltBytes = Base64.getDecoder().decode(userSalt);
            SecretKey vaultKey = deriveKeyWithSalt(masterKey, saltBytes);
            log.debug("Vault unlocked for user session: {}", truncateHash(token));
            return new CachedKey(vaultKey);
        });
    }


    public List<Map<String, String>> getPasswords(String sessionKey) throws Exception {
        SecretKey key = getVaultKey(sessionKey);
        List<PasswordEntity> entities = passwordRepository.findAll();
        List<Map<String, String>> result = new ArrayList<>(entities.size());

        for (PasswordEntity e : entities) {
            result.add(Map.of(
                    "service", e.getService(),
                    "login", decrypt(e.getEncryptedLogin(), key),
                    "password", decrypt(e.getEncryptedPassword(), key)
            ));
        }
        log.debug("Returned {} password entries for session: {}", result.size(), truncateHash(sessionKey));
        return result;
    }


    public Map<String, String> getDecryptedEntry(String sessionKey, String service) throws Exception {
        SecretKey key = getVaultKey(sessionKey);
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


    public void addPassword(String sessionKey, String service, String login, String password) throws Exception {
        validateInput(service, login, password);
        SecretKey key = getVaultKey(sessionKey);

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
        log.info("Added password entry for service: {} (session: {})", service, truncateHash(sessionKey));
    }


    public void updatePassword(String sessionKey, String service, String login, String password) throws Exception {
        validateInput(service, login, password);
        SecretKey key = getVaultKey(sessionKey);

        PasswordEntity entity = passwordRepository.findByService(service)
                .orElseThrow(() -> {
                    log.warn("Update failed: entry not found for service: {}", service);
                    return new IllegalArgumentException("Запись не найдена");
                });

        entity.setEncryptedLogin(encrypt(login, key));
        entity.setEncryptedPassword(encrypt(password, key));
        passwordRepository.save(entity);
        log.info("Updated password entry for service: {} (session: {})", service, truncateHash(sessionKey));
    }


    public void deletePassword(String sessionKey, String service) {
        if (service == null || service.isBlank()) {
            throw new IllegalArgumentException("Service name cannot be empty");
        }
        if (!passwordRepository.existsByService(service)) {
            log.warn("Delete failed: entry not found for service: {}", service);
            throw new IllegalArgumentException("Запись не найдена");
        }
        passwordRepository.deleteByService(service);
        log.info("Deleted password entry for service: {} (session: {})", service, truncateHash(sessionKey));
    }

    // =================================================================
    // ================= ПРИВАТНЫЕ МЕТОДЫ: КРИПТОГРАФИЯ ================
    // =================================================================

    /**
     * @param masterKey мастер-ключ пользователя
     * @param salt уникальная соль пользователя (16+ байт, Base64-декодированная)
     */
    private SecretKey deriveKeyWithSalt(String masterKey, byte[] salt) {
        Objects.requireNonNull(salt, "Salt cannot be null");
        if (salt.length < 16) throw new IllegalArgumentException("Salt must be at least 16 bytes");

        PKCS5S2ParametersGenerator gen = new PKCS5S2ParametersGenerator(new SHA256Digest());
        gen.init(
                masterKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                salt,
                PBKDF2_ITERATIONS
        );
        byte[] keyBytes = ((KeyParameter) gen.generateDerivedParameters(KEY_LENGTH_BITS)).getKey();
        return new SecretKeySpec(keyBytes, "AES");
    }


    private SecretKey deriveKeyLegacy(String masterKey) {
        byte[] salt = "password-manager-fixed-salt-v1".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return deriveKeyWithSalt(masterKey, salt);
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


    private String encrypt(String plaintext, SecretKey key) throws Exception {
        if (plaintext == null) throw new IllegalArgumentException("Cannot encrypt null");

        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv));

        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] result = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);

        return Base64.getEncoder().encodeToString(result);
    }


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
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv));

        byte[] plaintext = cipher.doFinal(ciphertext); 
        return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
    }

    // =================================================================
    // ============== ПРИВАТНЫЕ МЕТОДЫ: ВСПОМОГАТЕЛЬНЫЕ ================
    // =================================================================


    private SecretKey getVaultKey(String sessionKey) {
        if (sessionKey == null || sessionKey.isBlank()) {
            throw new SecurityException("Session key required");
        }
        CachedKey cached = vaultKeys.get(sessionKey);
        if (cached == null) {
            log.warn("Vault key not found for session: {}", truncateHash(sessionKey));
            throw new SecurityException("Vault not unlocked for this session");
        }
        cached.refresh();
        return cached.getKey();
    }


    private void validateInput(String service, String login, String password) {
        if (service == null || service.isBlank()) throw new IllegalArgumentException("Service required");
        if (login == null || login.isBlank()) throw new IllegalArgumentException("Login required");
        if (password == null) throw new IllegalArgumentException("Password cannot be null");
    }


    private void validateMasterKey(String masterKey) {
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalArgumentException("Master key cannot be empty");
        }
        if (masterKey.length() < 8) {
            throw new IllegalArgumentException("Master key must be at least 8 characters");
        }
    }


    private void cleanupExpiredKeys() {
        long now = System.currentTimeMillis();
        long timeoutMillis = SESSION_TIMEOUT_MINUTES * 60_000L;
        int removed = 0;

        Iterator<Map.Entry<String, CachedKey>> iterator = vaultKeys.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CachedKey> entry = iterator.next();
            if (now - entry.getValue().getLastAccess() > timeoutMillis) {
                iterator.remove();
                removed++;
                log.debug("Expired session removed: {}", truncateHash(entry.getKey()));
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
        log.info("Shutting down PasswordService: clearing {} cached keys", vaultKeys.size());
        vaultKeys.clear();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("PasswordService shutdown complete");
    }

    // =================================================================
    // ============= МЕТОДЫ ДЛЯ ТЕСТОВ (package-private) ===============
    // =================================================================

    int getCachedSessionsCount() {
        return vaultKeys.size();
    }


    void clearCacheForTests() {
        vaultKeys.clear();
    }
}