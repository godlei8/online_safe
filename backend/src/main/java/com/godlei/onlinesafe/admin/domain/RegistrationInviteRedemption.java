package com.godlei.onlinesafe.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "registration_invite_redemption")
public class RegistrationInviteRedemption {

    @Id
    @Column(name = "id", nullable = false, length = 36, updatable = false)
    private String id;

    @Column(name = "invite_id", nullable = false, length = 36)
    private String inviteId;

    @Column(name = "user_id", nullable = false, length = 36, unique = true)
    private String userId;

    @Column(name = "redeemed_at", nullable = false, updatable = false)
    private Instant redeemedAt;

    protected RegistrationInviteRedemption() {
    }

    private RegistrationInviteRedemption(String inviteId, String userId) {
        this.id = UUID.randomUUID().toString();
        this.inviteId = Objects.requireNonNull(inviteId);
        this.userId = Objects.requireNonNull(userId);
    }

    public static RegistrationInviteRedemption of(String inviteId, String userId) {
        return new RegistrationInviteRedemption(inviteId, userId);
    }

    @PrePersist
    void onCreate() {
        this.redeemedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getInviteId() {
        return inviteId;
    }

    public String getUserId() {
        return userId;
    }

    public Instant getRedeemedAt() {
        return redeemedAt;
    }
}
