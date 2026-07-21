package com.godlei.onlinesafe.admin.domain;

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
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "admin_user")
public class AdminUser {

    @Id
    @Column(name = "id", nullable = false, length = 36, updatable = false)
    private String id;

    @Column(name = "username", nullable = false, length = 64)
    private String username;

    @Column(name = "normalized_username", nullable = false, length = 64)
    private String normalizedUsername;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AdminUserStatus status;

    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected AdminUser() {
    }

    private AdminUser(String id, String username, String passwordHash) {
        this.id = id == null ? UUID.randomUUID().toString() : id;
        this.username = Objects.requireNonNull(username);
        this.normalizedUsername = username.trim().toLowerCase(Locale.ROOT);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.status = AdminUserStatus.ACTIVE;
        this.mfaEnabled = false;
    }

    public static AdminUser createActive(String username, String passwordHash) {
        return new AdminUser(null, username, passwordHash);
    }

    public static AdminUser createActive(String id, String username, String passwordHash) {
        return new AdminUser(id, username, passwordHash);
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

    public String getUsername() {
        return username;
    }

    public String getNormalizedUsername() {
        return normalizedUsername;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public AdminUserStatus getStatus() {
        return status;
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }
}
