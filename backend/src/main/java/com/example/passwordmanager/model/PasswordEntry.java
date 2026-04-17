package com.example.passwordmanager.model;

public record PasswordEntry(
    String service,
    String encryptedLogin,
    String encryptedPassword
) {}