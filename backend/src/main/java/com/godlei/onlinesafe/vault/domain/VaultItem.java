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

    @JdbcTypeCode(SqlTypes.BLOB)
    @Column(name = "ciphertext", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] ciphertext;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "nonce", nullable = false, length = 12)
    private byte[] nonce;

    @Column(name = "algo_version", nullable = false)
    private int algoVersion;

    @Column(name = "payload_version", nullable = false)
    private int payloadVersion;

    @Column(name = "key_id", nullable = false)
    private short keyId;

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
            int payloadVersion,
            short keyId
    ) {
        this.id = Objects.requireNonNull(id);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.ciphertext = Objects.requireNonNull(ciphertext);
        this.nonce = Objects.requireNonNull(nonce);
        this.algoVersion = algoVersion;
        this.payloadVersion = payloadVersion;
        this.keyId = keyId;
    }

    public static VaultItem create(
            String id,
            String ownerId,
            byte[] ciphertext,
            byte[] nonce,
            int algoVersion,
            int payloadVersion,
            int keyId
    ) {
        return new VaultItem(id, ownerId, ciphertext, nonce, algoVersion, payloadVersion, (short) keyId);
    }

    public void replaceCiphertext(
            byte[] ciphertext,
            byte[] nonce,
            int algoVersion,
            int payloadVersion,
            int keyId
    ) {
        this.ciphertext = Objects.requireNonNull(ciphertext);
        this.nonce = Objects.requireNonNull(nonce);
        this.algoVersion = algoVersion;
        this.payloadVersion = payloadVersion;
        this.keyId = (short) keyId;
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

    public int getKeyId() {
        return keyId;
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
