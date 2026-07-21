package com.godlei.onlinesafe.vault.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "vault_item")
public class VaultItem {

    @Id
    @Column(name = "id", nullable = false, length = 36, updatable = false)
    private String id;

    @Column(name = "owner_id", nullable = false, length = 36, updatable = false)
    private String ownerId;

    // Hibernate 7：LONG32VARBINARY 的 DDL 仍走 BLOB，默认 length=255 → TINYBLOB；显式锁定 LONGBLOB
    @JdbcTypeCode(SqlTypes.BLOB)
    @Column(name = "ciphertext", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] ciphertext;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "nonce", nullable = false, length = 24)
    private byte[] nonce;

    @Column(name = "algo_version", nullable = false)
    private int algoVersion;

    @Column(name = "payload_version", nullable = false)
    private int payloadVersion;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected VaultItem() {
    }

    private VaultItem(
            String id,
            String ownerId,
            byte[] ciphertext,
            byte[] nonce,
            int algoVersion,
            int payloadVersion
    ) {
        this.id = Objects.requireNonNull(id);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.ciphertext = Objects.requireNonNull(ciphertext);
        this.nonce = Objects.requireNonNull(nonce);
        this.algoVersion = algoVersion;
        this.payloadVersion = payloadVersion;
    }

    public static VaultItem create(
            String id,
            String ownerId,
            byte[] ciphertext,
            byte[] nonce,
            int algoVersion,
            int payloadVersion
    ) {
        return new VaultItem(id, ownerId, ciphertext, nonce, algoVersion, payloadVersion);
    }

    public void replaceCiphertext(byte[] ciphertext, byte[] nonce, int algoVersion, int payloadVersion) {
        this.ciphertext = Objects.requireNonNull(ciphertext);
        this.nonce = Objects.requireNonNull(nonce);
        this.algoVersion = algoVersion;
        this.payloadVersion = payloadVersion;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
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

    public String getOwnerId() {
        return ownerId;
    }

    public byte[] getCiphertext() {
        return ciphertext;
    }

    public byte[] getNonce() {
        return nonce;
    }

    public int getAlgoVersion() {
        return algoVersion;
    }

    public int getPayloadVersion() {
        return payloadVersion;
    }

    public Instant getDeletedAt() {
        return deletedAt;
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
