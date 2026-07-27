package com.godlei.onlinesafe.datarecovery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "data_recovery_batch")
@IdClass(DataRecoveryBatch.Pk.class)
public class DataRecoveryBatch {

    @Id
    @Column(name = "operation_id", nullable = false, length = 36)
    private String operationId;

    @Id
    @Column(name = "batch_no", nullable = false)
    private int batchNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BatchStatus status;

    @Column(name = "entry_count", nullable = false)
    private int entryCount;

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

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    public enum BatchStatus {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    protected DataRecoveryBatch() {
    }

    public static DataRecoveryBatch start(String operationId, int batchNo, int entryCount) {
        DataRecoveryBatch batch = new DataRecoveryBatch();
        batch.operationId = operationId;
        batch.batchNo = batchNo;
        batch.status = BatchStatus.RUNNING;
        batch.entryCount = entryCount;
        return batch;
    }

    public void succeed(int created, int restored, int skipped, int failed, Instant finishedAt) {
        this.status = BatchStatus.SUCCEEDED;
        this.createdCount = created;
        this.restoredCount = restored;
        this.skippedCount = skipped;
        this.failedCount = failed;
        this.finishedAt = finishedAt;
    }

    public void fail(String errorCode, Instant finishedAt) {
        this.status = BatchStatus.FAILED;
        this.errorCode = errorCode;
        this.finishedAt = finishedAt;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public String getOperationId() { return operationId; }
    public int getBatchNo() { return batchNo; }
    public BatchStatus getStatus() { return status; }
    public int getEntryCount() { return entryCount; }
    public int getCreatedCount() { return createdCount; }
    public int getRestoredCount() { return restoredCount; }
    public int getSkippedCount() { return skippedCount; }
    public int getFailedCount() { return failedCount; }
    public String getErrorCode() { return errorCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getFinishedAt() { return finishedAt; }

    public static final class Pk implements Serializable {
        private String operationId;
        private int batchNo;

        public Pk() {
        }

        public Pk(String operationId, int batchNo) {
            this.operationId = operationId;
            this.batchNo = batchNo;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk pk)) return false;
            return batchNo == pk.batchNo && Objects.equals(operationId, pk.operationId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(operationId, batchNo);
        }
    }
}
