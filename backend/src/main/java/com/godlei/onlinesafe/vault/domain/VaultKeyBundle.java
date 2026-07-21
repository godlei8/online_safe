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
@Table(name = "vault_key_bundle")
public class VaultKeyBundle {

    @Id
    @Column(name = "owner_id", nullable = false, length = 36, updatable = false)
    private String ownerId;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "kdf_salt", nullable = false, length = 64)
    private byte[] kdfSalt;

    @Column(name = "kdf_ops_limit", nullable = false)
    private long kdfOpsLimit;

    @Column(name = "kdf_mem_limit", nullable = false)
    private long kdfMemLimit;

    // Hibernate 7：LONG32VARBINARY 的 DDL 仍走 BLOB，默认 length=255 → TINYBLOB；显式锁定 LONGBLOB
    @JdbcTypeCode(SqlTypes.BLOB)
    @Column(name = "wrapped_dek_master", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] wrappedDekMaster;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "wrapped_dek_master_nonce", nullable = false, length = 24)
    private byte[] wrappedDekMasterNonce;

    @JdbcTypeCode(SqlTypes.BLOB)
    @Column(name = "wrapped_dek_recovery", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] wrappedDekRecovery;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "wrapped_dek_recovery_nonce", nullable = false, length = 24)
    private byte[] wrappedDekRecoveryNonce;

    @Column(name = "algo_version", nullable = false)
    private int algoVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected VaultKeyBundle() {
    }

    private VaultKeyBundle(
            String ownerId,
            byte[] kdfSalt,
            long kdfOpsLimit,
            long kdfMemLimit,
            byte[] wrappedDekMaster,
            byte[] wrappedDekMasterNonce,
            byte[] wrappedDekRecovery,
            byte[] wrappedDekRecoveryNonce,
            int algoVersion
    ) {
        this.ownerId = Objects.requireNonNull(ownerId);
        this.kdfSalt = Objects.requireNonNull(kdfSalt);
        this.kdfOpsLimit = kdfOpsLimit;
        this.kdfMemLimit = kdfMemLimit;
        this.wrappedDekMaster = Objects.requireNonNull(wrappedDekMaster);
        this.wrappedDekMasterNonce = Objects.requireNonNull(wrappedDekMasterNonce);
        this.wrappedDekRecovery = Objects.requireNonNull(wrappedDekRecovery);
        this.wrappedDekRecoveryNonce = Objects.requireNonNull(wrappedDekRecoveryNonce);
        this.algoVersion = algoVersion;
    }

    public static VaultKeyBundle create(
            String ownerId,
            byte[] kdfSalt,
            long kdfOpsLimit,
            long kdfMemLimit,
            byte[] wrappedDekMaster,
            byte[] wrappedDekMasterNonce,
            byte[] wrappedDekRecovery,
            byte[] wrappedDekRecoveryNonce,
            int algoVersion
    ) {
        return new VaultKeyBundle(
                ownerId,
                kdfSalt,
                kdfOpsLimit,
                kdfMemLimit,
                wrappedDekMaster,
                wrappedDekMasterNonce,
                wrappedDekRecovery,
                wrappedDekRecoveryNonce,
                algoVersion
        );
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

    public String getOwnerId() {
        return ownerId;
    }

    public byte[] getKdfSalt() {
        return kdfSalt;
    }

    public long getKdfOpsLimit() {
        return kdfOpsLimit;
    }

    public long getKdfMemLimit() {
        return kdfMemLimit;
    }

    public byte[] getWrappedDekMaster() {
        return wrappedDekMaster;
    }

    public byte[] getWrappedDekMasterNonce() {
        return wrappedDekMasterNonce;
    }

    public byte[] getWrappedDekRecovery() {
        return wrappedDekRecovery;
    }

    public byte[] getWrappedDekRecoveryNonce() {
        return wrappedDekRecoveryNonce;
    }

    public int getAlgoVersion() {
        return algoVersion;
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

    /**
     * 用新的登录密码信封与恢复密钥信封替换包装结果（DEK 本身不变，仅客户端重包装后上传）。
     */
    public void replaceWraps(
            byte[] kdfSalt,
            long kdfOpsLimit,
            long kdfMemLimit,
            byte[] wrappedDekMaster,
            byte[] wrappedDekMasterNonce,
            byte[] wrappedDekRecovery,
            byte[] wrappedDekRecoveryNonce,
            int algoVersion
    ) {
        this.kdfSalt = Objects.requireNonNull(kdfSalt);
        this.kdfOpsLimit = kdfOpsLimit;
        this.kdfMemLimit = kdfMemLimit;
        this.wrappedDekMaster = Objects.requireNonNull(wrappedDekMaster);
        this.wrappedDekMasterNonce = Objects.requireNonNull(wrappedDekMasterNonce);
        this.wrappedDekRecovery = Objects.requireNonNull(wrappedDekRecovery);
        this.wrappedDekRecoveryNonce = Objects.requireNonNull(wrappedDekRecoveryNonce);
        this.algoVersion = algoVersion;
    }
}
