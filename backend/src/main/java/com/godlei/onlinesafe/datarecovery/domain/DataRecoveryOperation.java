package com.godlei.onlinesafe.datarecovery.domain;

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
import java.util.UUID;

@Entity
@Table(name = "data_recovery_operation")
public class DataRecoveryOperation {

    @Id
    @Column(name = "id", nullable = false, length = 36, updatable = false)
    private String id;

    @Column(name = "owner_id", length = 36)
    private String ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 16)
    private RecoveryScope scope;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 40)
    private RecoveryOperationType operationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RecoveryOperationStatus status;

    @Column(name = "format_version")
    private Integer formatVersion;

    @Column(name = "source_backup_id", length = 36)
    private String sourceBackupId;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "processed_count", nullable = false)
    private int processedCount;

    @Column(name = "created_count", nullable = false)
    private int createdCount;

    @Column(name = "restored_count", nullable = false)
    private int restoredCount;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected DataRecoveryOperation() {
    }

    public static DataRecoveryOperation createUser(
            String ownerId,
            RecoveryOperationType type,
            Integer formatVersion,
            String sourceBackupId,
            int totalCount,
            Instant expiresAt
    ) {
        DataRecoveryOperation op = new DataRecoveryOperation();
        op.id = UUID.randomUUID().toString();
        op.ownerId = ownerId;
        op.scope = RecoveryScope.USER;
        op.operationType = type;
        op.status = RecoveryOperationStatus.CREATED;
        op.formatVersion = formatVersion;
        op.sourceBackupId = sourceBackupId;
        op.totalCount = totalCount;
        op.expiresAt = expiresAt;
        return op;
    }

    public void markRunning(Instant now) {
        this.status = RecoveryOperationStatus.RUNNING;
        this.startedAt = now;
    }

    public void addBatchCounts(int processed, int created, int restored, int skipped, int failed) {
        this.processedCount += processed;
        this.createdCount += created;
        this.restoredCount += restored;
        this.skippedCount += skipped;
        this.failedCount += failed;
    }

    public void complete(RecoveryOperationStatus status, Instant now, String errorCode) {
        this.status = status;
        this.finishedAt = now;
        this.errorCode = errorCode;
    }

    public void expire(Instant now) {
        this.status = RecoveryOperationStatus.EXPIRED;
        this.finishedAt = now;
        this.errorCode = "RESTORE_OPERATION_EXPIRED";
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

    public String getId() { return id; }
    public String getOwnerId() { return ownerId; }
    public RecoveryScope getScope() { return scope; }
    public RecoveryOperationType getOperationType() { return operationType; }
    public RecoveryOperationStatus getStatus() { return status; }
    public Integer getFormatVersion() { return formatVersion; }
    public String getSourceBackupId() { return sourceBackupId; }
    public int getTotalCount() { return totalCount; }
    public int getProcessedCount() { return processedCount; }
    public int getCreatedCount() { return createdCount; }
    public int getRestoredCount() { return restoredCount; }
    public int getSkippedCount() { return skippedCount; }
    public int getFailedCount() { return failedCount; }
    public String getErrorCode() { return errorCode; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
