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
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "registration_invite")
public class RegistrationInvite {

    @Id
    @Column(name = "id", nullable = false, length = 36, updatable = false)
    private String id;

    @Column(name = "code_hash", nullable = false, length = 64, unique = true)
    private String codeHash;

    @Column(name = "code_hint", nullable = false, length = 32)
    private String codeHint;

    @Column(name = "code_encrypted", length = 512)
    private String codeEncrypted;

    @Column(name = "max_uses", nullable = false)
    private int maxUses;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InviteStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 32)
    private InvitePurpose purpose;

    @Column(name = "created_by_admin_id", nullable = false, length = 36)
    private String createdByAdminId;

    @Column(name = "note", length = 200)
    private String note;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected RegistrationInvite() {
    }

    private RegistrationInvite(
            String codeHash,
            String codeHint,
            String codeEncrypted,
            int maxUses,
            Instant expiresAt,
            InvitePurpose purpose,
            String createdByAdminId,
            String note
    ) {
        this.id = UUID.randomUUID().toString();
        this.codeHash = Objects.requireNonNull(codeHash);
        this.codeHint = Objects.requireNonNull(codeHint);
        this.codeEncrypted = Objects.requireNonNull(codeEncrypted);
        this.maxUses = maxUses;
        this.usedCount = 0;
        this.expiresAt = expiresAt;
        this.status = InviteStatus.ACTIVE;
        this.purpose = Objects.requireNonNull(purpose);
        this.createdByAdminId = Objects.requireNonNull(createdByAdminId);
        this.note = note;
    }

    public static RegistrationInvite create(
            String codeHash,
            String codeHint,
            String codeEncrypted,
            int maxUses,
            Instant expiresAt,
            InvitePurpose purpose,
            String createdByAdminId,
            String note
    ) {
        if (maxUses < 1) {
            throw new IllegalArgumentException("maxUses must be >= 1");
        }
        return new RegistrationInvite(
                codeHash,
                codeHint,
                codeEncrypted,
                maxUses,
                expiresAt,
                purpose == null ? InvitePurpose.USER_REGISTRATION : purpose,
                createdByAdminId,
                note
        );
    }

    public void refreshDerivedStatus(Instant now) {
        if (status == InviteStatus.DISABLED) {
            return;
        }
        if (usedCount >= maxUses) {
            status = InviteStatus.EXHAUSTED;
            return;
        }
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            status = InviteStatus.EXPIRED;
            return;
        }
        status = InviteStatus.ACTIVE;
    }

    public boolean isUsable(Instant now) {
        refreshDerivedStatus(now);
        return status == InviteStatus.ACTIVE;
    }

    public void redeem(Instant now) {
        if (!isUsable(now)) {
            throw new IllegalStateException("invite not usable");
        }
        usedCount += 1;
        lastUsedAt = now;
        refreshDerivedStatus(now);
    }

    public void disable() {
        this.status = InviteStatus.DISABLED;
    }

    public boolean isSingleUse() {
        return maxUses == 1;
    }

    public boolean isExpiringSoon(Instant now, Instant horizon) {
        return status == InviteStatus.ACTIVE
                && expiresAt != null
                && expiresAt.isAfter(now)
                && !expiresAt.isAfter(horizon);
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

    public String getCodeHash() {
        return codeHash;
    }

    public String getCodeHint() {
        return codeHint;
    }

    public String getCodeEncrypted() {
        return codeEncrypted;
    }

    public int getMaxUses() {
        return maxUses;
    }

    public int getUsedCount() {
        return usedCount;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public InviteStatus getStatus() {
        return status;
    }

    public InvitePurpose getPurpose() {
        return purpose;
    }

    public String getCreatedByAdminId() {
        return createdByAdminId;
    }

    public String getNote() {
        return note;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
