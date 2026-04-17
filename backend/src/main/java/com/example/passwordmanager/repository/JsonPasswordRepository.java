package com.example.passwordmanager.repository;

import com.example.passwordmanager.model.PasswordEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Repository
public class JsonPasswordRepository {

    private static final String DATA_FILE = "backend/passwords.json";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private List<PasswordEntity> data = new ArrayList<>();

    @PostConstruct
    public void init() throws IOException {
        File file = new File(DATA_FILE);
        if (file.exists() && file.length() > 0) {
            data = objectMapper.readValue(file, new TypeReference<List<PasswordEntity>>() {});
        }
    }

    @PreDestroy
    public void saveOnShutdown() throws IOException {
        saveToFile();
    }

    private void saveToFile() throws IOException {
        lock.writeLock().lock();
        try {
            File file = new File(DATA_FILE);
            file.getParentFile().mkdirs();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, data);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<PasswordEntity> findAll() {
        lock.readLock().lock();
        try { return new ArrayList<>(data); }
        finally { lock.readLock().unlock(); }
    }

    public Optional<PasswordEntity> findByService(String service) {
        lock.readLock().lock();
        try {
            return data.stream().filter(e -> service.equals(e.getService())).findFirst();
        } finally { lock.readLock().unlock(); }
    }

    public boolean existsByService(String service) {
        lock.readLock().lock();
        try { return data.stream().anyMatch(e -> service.equals(e.getService())); }
        finally { lock.readLock().unlock(); }
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
            throw new RuntimeException("Failed to save to JSON", e);
        } finally { lock.writeLock().unlock(); }
    }

    public void deleteByService(String service) {
        lock.writeLock().lock();
        try {
            data.removeIf(e -> service.equals(e.getService()));
            saveToFile();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save after delete", e);
        } finally { lock.writeLock().unlock(); }
    }
}