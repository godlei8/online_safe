package com.godlei.onlinesafe.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @Column(name = "id", nullable = false, length = 36, updatable = false)
    private String id;

    @Column(name = "phone", nullable = false, length = 32)
    private String phone;

    @Column(name = "username", nullable = false, length = 64)
    private String username;

    @Column(name = "normalized_username", nullable = false, length = 64)
    private String normalizedUsername;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AppUserStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected AppUser() {
    }

    private AppUser(String phone, String username, String normalizedUsername, String passwordHash) {
        this.id = UUID.randomUUID().toString();
        this.phone = Objects.requireNonNull(phone);
        this.username = Objects.requireNonNull(username);
        this.normalizedUsername = Objects.requireNonNull(normalizedUsername);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.phoneVerified = false;
        this.status = AppUserStatus.ACTIVE;
    }

    public static AppUser register(String phone, String username, String normalizedUsername, String passwordHash) {
        return new AppUser(phone, username, normalizedUsername, passwordHash);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getPhone() {
        return phone;
    }

    public String getUsername() {
        return username;
    }

    public String getNormalizedUsername() {
        return normalizedUsername;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isPhoneVerified() {
        return phoneVerified;
    }

    public AppUserStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
