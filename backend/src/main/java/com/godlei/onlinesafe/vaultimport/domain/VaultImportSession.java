package com.godlei.onlinesafe.vaultimport.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "vault_import_session")
public class VaultImportSession {

    @Id
    @Column(name = "id", nullable = false, length = 36, updatable = false)
    private String id;

    @Column(name = "owner_id", nullable = false, length = 36, updatable = false)
    private String ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ImportSessionStatus status;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_format", nullable = false, length = 16)
    private ImportFileFormat fileFormat;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "ready_count", nullable = false)
    private int readyCount;

    @Column(name = "needs_review_count", nullable = false)
    private int needsReviewCount;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;

    @Column(name = "model_provider", length = 64)
    private String modelProvider;

    @Column(name = "model_name", length = 128)
    private String modelName;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "progress_message", length = 160)
    private String progressMessage;

    @JdbcTypeCode(SqlTypes.BLOB)
    @Column(name = "candidates_ciphertext", columnDefinition = "LONGBLOB")
    private byte[] candidatesCiphertext;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "candidates_nonce", length = 12)
    private byte[] candidatesNonce;

    @Column(name = "candidates_algo_version")
    private Integer candidatesAlgoVersion;

    @Column(name = "candidates_key_id")
    private Short candidatesKeyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "committed_at")
    private Instant committedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected VaultImportSession() {
    }

    private VaultImportSession(
            String id,
            String ownerId,
            String fileName,
            ImportFileFormat fileFormat,
            long byteSize,
            Instant expiresAt
    ) {
        this.id = Objects.requireNonNull(id);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.fileName = Objects.requireNonNull(fileName);
        this.fileFormat = Objects.requireNonNull(fileFormat);
        this.byteSize = byteSize;
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.status = ImportSessionStatus.UPLOADING;
        this.rowCount = 0;
        this.readyCount = 0;
        this.needsReviewCount = 0;
        this.skippedCount = 0;
    }

    public static VaultImportSession create(
            String id,
            String ownerId,
            String fileName,
            ImportFileFormat fileFormat,
            long byteSize,
            Instant expiresAt
    ) {
        return new VaultImportSession(id, ownerId, fileName, fileFormat, byteSize, expiresAt);
    }

    public void markParsing(String progressMessage) {
        this.status = ImportSessionStatus.PARSING;
        this.errorCode = null;
        this.progressMessage = progressMessage;
    }

    public void markAiMapping(String progressMessage) {
        this.status = ImportSessionStatus.AI_MAPPING;
        this.progressMessage = progressMessage;
    }

    public void updateProgress(String progressMessage) {
        this.progressMessage = progressMessage;
    }

    public void markReady(
            int rowCount,
            int readyCount,
            int needsReviewCount,
            int skippedCount,
            String modelProvider,
            String modelName
    ) {
        this.status = ImportSessionStatus.READY;
        this.rowCount = rowCount;
        this.readyCount = readyCount;
        this.needsReviewCount = needsReviewCount;
        this.skippedCount = skippedCount;
        this.modelProvider = modelProvider;
        this.modelName = modelName;
        this.errorCode = null;
        this.progressMessage = "识别完成，请核对结果";
    }

    public void markFailed(String errorCode, String progressMessage) {
        this.status = ImportSessionStatus.FAILED;
        this.errorCode = errorCode;
        this.progressMessage = progressMessage;
        clearCandidates();
    }

    public void markCommitting() {
        this.status = ImportSessionStatus.COMMITTING;
    }

    public void markCommitted() {
        this.status = ImportSessionStatus.COMMITTED;
        this.committedAt = Instant.now();
        clearCandidates();
    }

    public void markDiscarded() {
        this.status = ImportSessionStatus.DISCARDED;
        clearCandidates();
    }

    public void storeCandidates(
            byte[] ciphertext,
            byte[] nonce,
            int algoVersion,
            int keyId
    ) {
        this.candidatesCiphertext = Objects.requireNonNull(ciphertext);
        this.candidatesNonce = Objects.requireNonNull(nonce);
        this.candidatesAlgoVersion = algoVersion;
        this.candidatesKeyId = (short) keyId;
    }

    public void clearCandidates() {
        this.candidatesCiphertext = null;
        this.candidatesNonce = null;
        this.candidatesAlgoVersion = null;
        this.candidatesKeyId = null;
    }

    public void updateCounts(int readyCount, int needsReviewCount, int skippedCount) {
        this.readyCount = readyCount;
        this.needsReviewCount = needsReviewCount;
        this.skippedCount = skippedCount;
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public boolean hasCandidates() {
        return candidatesCiphertext != null && candidatesNonce != null;
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

    public ImportSessionStatus getStatus() {
        return status;
    }

    public String getFileName() {
        return fileName;
    }

    public ImportFileFormat getFileFormat() {
        return fileFormat;
    }

    public long getByteSize() {
        return byteSize;
    }

    public int getRowCount() {
        return rowCount;
    }

    public int getReadyCount() {
        return readyCount;
    }

    public int getNeedsReviewCount() {
        return needsReviewCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public String getModelProvider() {
        return modelProvider;
    }

    public String getModelName() {
        return modelName;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getProgressMessage() {
        return progressMessage;
    }

    public byte[] getCandidatesCiphertext() {
        return candidatesCiphertext;
    }

    public byte[] getCandidatesNonce() {
        return candidatesNonce;
    }

    public Integer getCandidatesAlgoVersion() {
        return candidatesAlgoVersion;
    }

    public Short getCandidatesKeyId() {
        return candidatesKeyId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCommittedAt() {
        return committedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
