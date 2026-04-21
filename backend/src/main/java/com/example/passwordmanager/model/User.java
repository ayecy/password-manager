package com.example.passwordmanager.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class User {
    private final String username;
    private final String passwordHash; 
    private final String masterKeySalt; 
    private final long createdAt;

    public User(String username, String passwordHash, String masterKeySalt) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Username cannot be empty");
        if (passwordHash == null || passwordHash.isBlank()) throw new IllegalArgumentException("Password hash required");
        if (masterKeySalt == null || masterKeySalt.isBlank()) throw new IllegalArgumentException("Salt required");
        this.username = username;
        this.passwordHash = passwordHash;
        this.masterKeySalt = masterKeySalt;
        this.createdAt = System.currentTimeMillis();
    }

    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getMasterKeySalt() { return masterKeySalt; }
    public long getCreatedAt() { return createdAt; }
}