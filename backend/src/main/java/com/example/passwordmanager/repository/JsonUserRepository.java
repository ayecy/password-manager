package com.example.passwordmanager.repository;

import com.example.passwordmanager.model.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Repository
public final class JsonUserRepository {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private List<User> data = new ArrayList<>();
    private final Path dataFilePath;

    public JsonUserRepository(@Value("${app.users.file:users.json}") String filePath) {
        Path path = Paths.get(filePath);
        if (!path.isAbsolute()) path = Paths.get(System.getProperty("user.dir"), filePath).normalize();
        this.dataFilePath = path.toAbsolutePath();
    }

    @PostConstruct
    public void init() throws IOException {
        var parent = dataFilePath.getParent();
        if (parent != null && !Files.exists(parent)) Files.createDirectories(parent);
        if (!Files.exists(dataFilePath)) Files.writeString(dataFilePath, "[]", StandardCharsets.UTF_8);
        String content = Files.readString(dataFilePath, StandardCharsets.UTF_8);
        this.data = content.isBlank() ? new ArrayList<>() : objectMapper.readValue(content, new TypeReference<>() {});
    }

    private void save() throws IOException {
        lock.writeLock().lock();
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(dataFilePath.toFile(), data);
        } finally { lock.writeLock().unlock(); }
    }

    public Optional<User> findByUsername(String username) {
        lock.readLock().lock();
        try { return data.stream().filter(u -> username.equals(u.getUsername())).findFirst(); }
        finally { lock.readLock().unlock(); }
    }

    public boolean existsByUsername(String username) {
        lock.readLock().lock();
        try { return data.stream().anyMatch(u -> username.equals(u.getUsername())); }
        finally { lock.readLock().unlock(); }
    }

    public User save(User user) {
        lock.writeLock().lock();
        try {
            Optional<User> existing = findByUsername(user.getUsername());
            if (existing.isPresent()) {
                int idx = data.indexOf(existing.get());
                data.set(idx, user);
            } else { data.add(user); }
            save();
            return user;
        } catch (IOException e) { throw new RuntimeException("Failed to persist users", e); }
        finally { lock.writeLock().unlock(); }
    }
}