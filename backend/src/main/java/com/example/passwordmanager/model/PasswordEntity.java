package com.example.passwordmanager.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PasswordEntity {

    private String service;
    private String encryptedLogin;
    private String encryptedPassword;

    public PasswordEntity() {}

    public PasswordEntity(String service, String encryptedLogin, String encryptedPassword) {
        this.service = service;
        this.encryptedLogin = encryptedLogin;
        this.encryptedPassword = encryptedPassword;
    }

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }

    public String getEncryptedLogin() { return encryptedLogin; }
    public void setEncryptedLogin(String encryptedLogin) { this.encryptedLogin = encryptedLogin; }

    public String getEncryptedPassword() { return encryptedPassword; }
    public void setEncryptedPassword(String encryptedPassword) { this.encryptedPassword = encryptedPassword; }
}