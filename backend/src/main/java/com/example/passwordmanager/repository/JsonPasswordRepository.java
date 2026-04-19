package com.example.passwordmanager.repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.example.passwordmanager.model.PasswordEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Repository
public class JsonPasswordRepository {

    private static final Logger log = LoggerFactory.getLogger(JsonPasswordRepository.class);

    @Value("${app.data.file:passwords.json}")
    private String dataFilePath;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private List<PasswordEntity> data = new ArrayList<>();
    private Path dataFilePathResolved;

    @PostConstruct
    public void init() throws IOException {
        log.info("Data file path pattern: {}", dataFilePath);
        
        Path path = Paths.get(dataFilePath);
        if (!path.isAbsolute()) {
            String userDir = System.getProperty("user.dir");
            log.debug("Resolving against user.dir: {}", userDir);
            path = Paths.get(userDir, dataFilePath).normalize();
        }
        this.dataFilePathResolved = path.toAbsolutePath();
        log.info("Resolved absolute path: {}", dataFilePathResolved);

        Path parent = dataFilePathResolved.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        if (!Files.exists(dataFilePathResolved)) {
            Files.writeString(dataFilePathResolved, "[]", StandardCharsets.UTF_8);
        }

        String content = Files.readString(dataFilePathResolved, StandardCharsets.UTF_8);
        this.data = content.isBlank() 
                ? new ArrayList<>() 
                : objectMapper.readValue(content, new TypeReference<List<PasswordEntity>>() {});
    }

    @PreDestroy
    public void saveOnShutdown() {
        try {
            saveToFile();
        } catch (IOException e) {
            log.error("Failed to save data on shutdown", e);
        }
    }

    private void saveToFile() throws IOException {
        lock.writeLock().lock();
        try {
            objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValue(dataFilePathResolved.toFile(), data);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<PasswordEntity> findAll() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(data);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<PasswordEntity> findByService(String service) {
        lock.readLock().lock();
        try {
            return data.stream()
                    .filter(e -> service.equals(e.getService()))
                    .findFirst();
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean existsByService(String service) {
        lock.readLock().lock();
        try {
            return data.stream().anyMatch(e -> service.equals(e.getService()));
        } finally {
            lock.readLock().unlock();
        }
    }

    public PasswordEntity save(PasswordEntity entity) {
        lock.writeLock().lock();
        try {
            Optional<PasswordEntity> existing = findByService(entity.getService());
            if (existing.isPresent()) {
                int idx = data.indexOf(existing.get());
                data.set(idx, entity);
            } else {
                data.add(entity);
            }
            saveToFile();
            return entity;
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist data to JSON", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void deleteByService(String service) {
        lock.writeLock().lock();
        try {
            boolean removed = data.removeIf(e -> service.equals(e.getService()));
            if (removed) {
                saveToFile();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete entry and save to JSON", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String getResolvedDataFilePath() {
        return dataFilePathResolved != null 
                ? dataFilePathResolved.toString() 
                : "not initialized";
    }
}