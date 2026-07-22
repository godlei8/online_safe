package com.godlei.onlinesafe.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "sms_verification")
public class SmsVerification {

    @Id
    @Column(name = "id", nullable = false, length = 36, updatable = false)
    private String id;

    @Column(name = "phone", nullable = false, length = 32)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 32)
    private SmsPurpose purpose;

    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "send_count_window", nullable = false)
    private int sendCountWindow;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    protected SmsVerification() {
    }

    private SmsVerification(
            String phone,
            SmsPurpose purpose,
            String codeHash,
            Instant expiresAt,
            int sendCountWindow,
            String clientIp
    ) {
        this.id = UUID.randomUUID().toString();
        this.phone = Objects.requireNonNull(phone);
        this.purpose = Objects.requireNonNull(purpose);
        this.codeHash = Objects.requireNonNull(codeHash);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.sendCountWindow = sendCountWindow;
        this.clientIp = clientIp;
    }

    public static SmsVerification create(
            String phone,
            SmsPurpose purpose,
            String codeHash,
            Instant expiresAt,
            int sendCountWindow,
            String clientIp
    ) {
        return new SmsVerification(phone, purpose, codeHash, expiresAt, sendCountWindow, clientIp);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getPhone() {
        return phone;
    }

    public SmsPurpose getPurpose() {
        return purpose;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public int getSendCountWindow() {
        return sendCountWindow;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getClientIp() {
        return clientIp;
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now) || expiresAt.equals(now);
    }

    public void consume(Instant at) {
        if (this.consumedAt != null) {
            throw new IllegalStateException("验证码已使用");
        }
        this.consumedAt = Objects.requireNonNull(at);
    }
}
