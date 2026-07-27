package com.godlei.onlinesafe.datarecovery.application;

import com.godlei.onlinesafe.audit.application.SecurityAuditService;
import com.godlei.onlinesafe.audit.domain.AuditActorType;
import com.godlei.onlinesafe.audit.domain.AuditEventType;
import com.godlei.onlinesafe.audit.domain.AuditResult;
import com.godlei.onlinesafe.datarecovery.domain.DataRecoveryBatch;
import com.godlei.onlinesafe.datarecovery.domain.DataRecoveryOperation;
import com.godlei.onlinesafe.datarecovery.domain.RecoveryOperationStatus;
import com.godlei.onlinesafe.datarecovery.domain.RecoveryOperationType;
import com.godlei.onlinesafe.datarecovery.infrastructure.DataRecoveryBatchRepository;
import com.godlei.onlinesafe.datarecovery.infrastructure.DataRecoveryOperationRepository;
import com.godlei.onlinesafe.datarecovery.web.CreateRestoreOperationRequest;
import com.godlei.onlinesafe.datarecovery.web.RestoreBatchRequest;
import com.godlei.onlinesafe.datarecovery.web.RestoreBatchResponse;
import com.godlei.onlinesafe.datarecovery.web.RestoreOperationResponse;
import com.godlei.onlinesafe.datarecovery.web.RestorePreflightRequest;
import com.godlei.onlinesafe.datarecovery.web.RestorePreflightResponse;
import com.godlei.onlinesafe.vault.application.InvalidVaultEnvelopeException;
import com.godlei.onlinesafe.vault.application.PrivateTemplateService;
import com.godlei.onlinesafe.vault.application.VaultItemService;
import com.godlei.onlinesafe.vault.application.VaultPayloadCipher;
import com.godlei.onlinesafe.vault.application.VaultRecordSupport;
import com.godlei.onlinesafe.vault.domain.PrivateTemplate;
import com.godlei.onlinesafe.vault.domain.VaultItem;
import com.godlei.onlinesafe.vault.infrastructure.PrivateTemplateRepository;
import com.godlei.onlinesafe.vault.infrastructure.VaultItemRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class VaultRestoreService {

    private static final Duration OPERATION_TTL = Duration.ofHours(24);

    private final VaultItemRepository itemRepository;
    private final PrivateTemplateRepository templateRepository;
    private final VaultPayloadCipher cipher;
    private final DataRecoveryOperationRepository operationRepository;
    private final DataRecoveryBatchRepository batchRepository;
    private final SecurityAuditService securityAuditService;
    private final Clock clock;
    private final int batchSize;

    public VaultRestoreService(
            VaultItemRepository itemRepository,
            PrivateTemplateRepository templateRepository,
            VaultPayloadCipher cipher,
            DataRecoveryOperationRepository operationRepository,
            DataRecoveryBatchRepository batchRepository,
            SecurityAuditService securityAuditService,
            Clock clock,
            @Value("${app.data-recovery.batch-size:100}") int batchSize
    ) {
        this.itemRepository = itemRepository;
        this.templateRepository = templateRepository;
        this.cipher = cipher;
        this.operationRepository = operationRepository;
        this.batchRepository = batchRepository;
        this.securityAuditService = securityAuditService;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Transactional(readOnly = true)
    public RestorePreflightResponse preflight(String ownerId, RestorePreflightRequest request) {
        if (request == null || request.entries() == null) {
            throw new DataRecoveryException("BACKUP_SCHEMA_INVALID", "备份内容校验失败");
        }
        int create = 0;
        int restoreDeleted = 0;
        int skip = 0;
        int reassign = 0;
        int invalid = 0;
        int trashInBackup = 0;
        List<RestorePreflightResponse.RestorePreflightDecision> decisions = new ArrayList<>();
        for (RestorePreflightRequest.RestorePreflightEntry entry : request.entries()) {
            if (entry.deletedInBackup()) {
                trashInBackup++;
            }
            String action = decide(ownerId, entry.type(), entry.sourceId(), entry.deletedInBackup(), true);
            if (entry.deletedInBackup()) {
                // 预览默认不复活回收站资产；计数单独展示
                decisions.add(new RestorePreflightResponse.RestorePreflightDecision(
                        entry.type(), entry.sourceId(), "SKIP_TRASH_DEFAULT"));
                continue;
            }
            switch (action) {
                case "CREATE" -> create++;
                case "RESTORE_DELETED" -> restoreDeleted++;
                case "SKIP_ACTIVE_CONFLICT" -> skip++;
                case "REASSIGN_ID" -> reassign++;
                default -> invalid++;
            }
            decisions.add(new RestorePreflightResponse.RestorePreflightDecision(
                    entry.type(), entry.sourceId(), action));
        }
        return new RestorePreflightResponse(
                create, restoreDeleted, skip, reassign, invalid, trashInBackup, decisions);
    }

    @Transactional
    public RestoreOperationResponse createOperation(
            String ownerId,
            String username,
            CreateRestoreOperationRequest request
    ) {
        expireIfNeeded(ownerId);
        if (operationRepository.existsByOwnerIdAndStatus(ownerId, RecoveryOperationStatus.RUNNING)) {
            throw new DataRecoveryException("RESTORE_ALREADY_RUNNING", "当前已有恢复任务正在执行");
        }
        if (request == null || request.totalCount() < 0) {
            throw new DataRecoveryException("BACKUP_SCHEMA_INVALID", "备份内容校验失败");
        }
        Instant now = clock.instant();
        DataRecoveryOperation op = DataRecoveryOperation.createUser(
                ownerId,
                RecoveryOperationType.USER_RESTORE,
                request.formatVersion() == null ? VaultBackupSnapshotService.FORMAT_VERSION : request.formatVersion(),
                request.sourceBackupId(),
                request.totalCount(),
                now.plus(OPERATION_TTL)
        );
        op.markRunning(now);
        operationRepository.save(op);
        securityAuditService.recordInTx(
                AuditEventType.USER_BACKUP_RESTORE_STARTED,
                AuditResult.SUCCESS,
                AuditActorType.USER,
                ownerId,
                username,
                null,
                null,
                null,
                null,
                Map.of(
                        "totalCount", request.totalCount(),
                        "formatVersion", op.getFormatVersion() == null ? 1 : op.getFormatVersion()
                )
        );
        return toResponse(op);
    }

    @Transactional
    public RestoreBatchResponse submitBatch(
            String ownerId,
            String operationId,
            int batchNo,
            RestoreBatchRequest request
    ) {
        DataRecoveryOperation op = requireOwnedOperation(ownerId, operationId);
        ensureRunnable(op);
        if (batchNo < 1) {
            throw new DataRecoveryException("RESTORE_BATCH_CONFLICT", "恢复批次状态冲突");
        }
        Optional<DataRecoveryBatch> existing = batchRepository.findByOperationIdAndBatchNo(operationId, batchNo);
        if (existing.isPresent()) {
            DataRecoveryBatch batch = existing.get();
            if (batch.getStatus() == DataRecoveryBatch.BatchStatus.SUCCEEDED) {
                return new RestoreBatchResponse(
                        batchNo,
                        batch.getStatus().name(),
                        batch.getCreatedCount(),
                        batch.getRestoredCount(),
                        batch.getSkippedCount(),
                        batch.getFailedCount()
                );
            }
            throw new DataRecoveryException("RESTORE_BATCH_CONFLICT", "恢复批次状态冲突");
        }
        if (request == null || request.entries() == null || request.entries().isEmpty()) {
            throw new DataRecoveryException("BACKUP_SCHEMA_INVALID", "备份内容校验失败");
        }
        if (request.entries().size() > batchSize) {
            throw new DataRecoveryException("BACKUP_LIMIT_EXCEEDED", "备份内容超过允许范围");
        }

        DataRecoveryBatch batch = DataRecoveryBatch.start(operationId, batchNo, request.entries().size());
        batchRepository.save(batch);

        int created = 0;
        int restored = 0;
        int skipped = 0;
        int failed = 0;
        boolean includeTrash = request.includeTrashAssets();
        Instant now = clock.instant();
        try {
            for (RestoreBatchRequest.RestoreBatchEntry entry : request.entries()) {
                String action = decide(ownerId, entry.type(), entry.sourceId(), entry.deletedInBackup(), includeTrash);
                switch (action) {
                    case "SKIP_ACTIVE_CONFLICT", "INVALID" -> {
                        if ("INVALID".equals(action)) {
                            failed++;
                        } else {
                            skipped++;
                        }
                    }
                    case "CREATE", "REASSIGN_ID" -> {
                        applyCreate(ownerId, entry, "REASSIGN_ID".equals(action));
                        created++;
                    }
                    case "RESTORE_DELETED" -> {
                        applyRestoreDeleted(ownerId, entry);
                        restored++;
                    }
                    default -> failed++;
                }
            }
            batch.succeed(created, restored, skipped, failed, now);
            batchRepository.save(batch);
            op.addBatchCounts(request.entries().size(), created, restored, skipped, failed);
            operationRepository.save(op);
            return new RestoreBatchResponse(batchNo, batch.getStatus().name(), created, restored, skipped, failed);
        } catch (DataRecoveryException exception) {
            batch.fail(exception.getCode(), now);
            batchRepository.save(batch);
            throw exception;
        } catch (RuntimeException exception) {
            batch.fail("RESTORE_FAILED", now);
            batchRepository.save(batch);
            throw new DataRecoveryException("RESTORE_FAILED", "数据恢复未完成，请查看恢复结果");
        }
    }

    @Transactional
    public RestoreOperationResponse complete(String ownerId, String username, String operationId) {
        DataRecoveryOperation op = requireOwnedOperation(ownerId, operationId);
        ensureRunnable(op);
        Instant now = clock.instant();
        RecoveryOperationStatus status = op.getFailedCount() > 0
                ? RecoveryOperationStatus.PARTIAL
                : RecoveryOperationStatus.SUCCEEDED;
        op.complete(status, now, null);
        operationRepository.save(op);
        securityAuditService.recordInTx(
                AuditEventType.USER_BACKUP_RESTORE_SUCCEEDED,
                AuditResult.SUCCESS,
                AuditActorType.USER,
                ownerId,
                username,
                null,
                null,
                null,
                null,
                Map.of(
                        "createdCount", op.getCreatedCount(),
                        "restoredCount", op.getRestoredCount(),
                        "skippedCount", op.getSkippedCount()
                )
        );
        return toResponse(op);
    }

    @Transactional(readOnly = true)
    public RestoreOperationResponse get(String ownerId, String operationId) {
        return toResponse(requireOwnedOperation(ownerId, operationId));
    }

    private void applyCreate(String ownerId, RestoreBatchRequest.RestoreBatchEntry entry, boolean forceNewId) {
        String id = forceNewId || idTakenByOther(ownerId, entry.type(), entry.sourceId())
                ? UUID.randomUUID().toString()
                : entry.sourceId().trim();
        if (!forceNewId) {
            VaultRecordSupport.requireUuid(id);
        }
        JsonNode payload = entry.payload();
        if ("ITEM".equalsIgnoreCase(entry.type())) {
            VaultRecordSupport.requireItemPayload(payload);
            if (itemRepository.existsById(id)) {
                id = UUID.randomUUID().toString();
            }
            VaultPayloadCipher.SealedPayload sealed = cipher.encrypt(ownerId, VaultItemService.ENTITY_TYPE, id, payload);
            VaultItem item = VaultItem.create(
                    id, ownerId, sealed.ciphertext(), sealed.nonce(),
                    sealed.algoVersion(), sealed.payloadVersion(), sealed.keyId());
            if (entry.deletedInBackup()) {
                item.softDelete();
            }
            itemRepository.saveAndFlush(item);
        } else if ("PRIVATE_TEMPLATE".equalsIgnoreCase(entry.type()) || "TEMPLATE".equalsIgnoreCase(entry.type())) {
            VaultRecordSupport.requireTemplatePayload(payload);
            if (templateRepository.existsById(id)) {
                id = UUID.randomUUID().toString();
            }
            VaultPayloadCipher.SealedPayload sealed = cipher.encrypt(
                    ownerId, PrivateTemplateService.ENTITY_TYPE, id, payload);
            PrivateTemplate template = PrivateTemplate.create(
                    id, ownerId, sealed.ciphertext(), sealed.nonce(),
                    sealed.algoVersion(), sealed.payloadVersion(), sealed.keyId());
            if (entry.deletedInBackup()) {
                template.softDelete();
            }
            templateRepository.saveAndFlush(template);
        } else {
            throw new DataRecoveryException("BACKUP_SCHEMA_INVALID", "备份内容校验失败");
        }
    }

    private void applyRestoreDeleted(String ownerId, RestoreBatchRequest.RestoreBatchEntry entry) {
        JsonNode payload = entry.payload();
        if ("ITEM".equalsIgnoreCase(entry.type())) {
            VaultRecordSupport.requireItemPayload(payload);
            VaultItem item = itemRepository.findByIdAndOwnerIdAndDeletedAtIsNotNull(entry.sourceId(), ownerId)
                    .orElseThrow(() -> new DataRecoveryException("BACKUP_SCHEMA_INVALID", "备份内容校验失败"));
            VaultPayloadCipher.SealedPayload sealed = cipher.encrypt(
                    ownerId, VaultItemService.ENTITY_TYPE, item.getId(), payload);
            item.replaceCiphertext(
                    sealed.ciphertext(), sealed.nonce(),
                    sealed.algoVersion(), sealed.payloadVersion(), sealed.keyId());
            item.restore();
            itemRepository.saveAndFlush(item);
        } else {
            VaultRecordSupport.requireTemplatePayload(payload);
            PrivateTemplate template = templateRepository.findByIdAndOwnerIdAndDeletedAtIsNotNull(
                            entry.sourceId(), ownerId)
                    .orElseThrow(() -> new DataRecoveryException("BACKUP_SCHEMA_INVALID", "备份内容校验失败"));
            VaultPayloadCipher.SealedPayload sealed = cipher.encrypt(
                    ownerId, PrivateTemplateService.ENTITY_TYPE, template.getId(), payload);
            template.replaceCiphertext(
                    sealed.ciphertext(), sealed.nonce(),
                    sealed.algoVersion(), sealed.payloadVersion(), sealed.keyId());
            template.restore();
            templateRepository.saveAndFlush(template);
        }
    }

    private String decide(String ownerId, String type, String sourceId, boolean deletedInBackup, boolean includeTrash) {
        if (sourceId == null || sourceId.isBlank()) {
            return "INVALID";
        }
        try {
            VaultRecordSupport.requireUuid(sourceId);
        } catch (InvalidVaultEnvelopeException exception) {
            return "INVALID";
        }
        if (deletedInBackup && !includeTrash) {
            return "INVALID";
        }
        boolean item = "ITEM".equalsIgnoreCase(type);
        boolean template = "PRIVATE_TEMPLATE".equalsIgnoreCase(type) || "TEMPLATE".equalsIgnoreCase(type);
        if (!item && !template) {
            return "INVALID";
        }
        if (item) {
            Optional<VaultItem> owned = itemRepository.findByIdAndOwnerId(sourceId, ownerId);
            if (owned.isEmpty()) {
                if (itemRepository.existsById(sourceId)) {
                    return "REASSIGN_ID";
                }
                return "CREATE";
            }
            if (owned.get().isDeleted()) {
                return "RESTORE_DELETED";
            }
            return "SKIP_ACTIVE_CONFLICT";
        }
        Optional<PrivateTemplate> owned = templateRepository.findByIdAndOwnerId(sourceId, ownerId);
        if (owned.isEmpty()) {
            if (templateRepository.existsById(sourceId)) {
                return "REASSIGN_ID";
            }
            return "CREATE";
        }
        if (owned.get().isDeleted()) {
            return "RESTORE_DELETED";
        }
        return "SKIP_ACTIVE_CONFLICT";
    }

    private boolean idTakenByOther(String ownerId, String type, String sourceId) {
        if ("ITEM".equalsIgnoreCase(type)) {
            return itemRepository.existsById(sourceId)
                    && itemRepository.findByIdAndOwnerId(sourceId, ownerId).isEmpty();
        }
        return templateRepository.existsById(sourceId)
                && templateRepository.findByIdAndOwnerId(sourceId, ownerId).isEmpty();
    }

    private DataRecoveryOperation requireOwnedOperation(String ownerId, String operationId) {
        return operationRepository.findByIdAndOwnerId(operationId, ownerId)
                .orElseThrow(() -> new DataRecoveryException("RESTORE_OPERATION_EXPIRED", "恢复任务已过期，请重新导入"));
    }

    private void ensureRunnable(DataRecoveryOperation op) {
        Instant now = clock.instant();
        if (op.getExpiresAt() != null && now.isAfter(op.getExpiresAt())) {
            op.expire(now);
            operationRepository.save(op);
            throw new DataRecoveryException("RESTORE_OPERATION_EXPIRED", "恢复任务已过期，请重新导入");
        }
        if (op.getStatus() != RecoveryOperationStatus.RUNNING
                && op.getStatus() != RecoveryOperationStatus.CREATED) {
            throw new DataRecoveryException("RESTORE_BATCH_CONFLICT", "恢复批次状态冲突");
        }
    }

    private void expireIfNeeded(String ownerId) {
        // no-op placeholder: RUNNING uniqueness is checked via existsByOwnerIdAndStatus
    }

    private static RestoreOperationResponse toResponse(DataRecoveryOperation op) {
        return new RestoreOperationResponse(
                op.getId(),
                op.getStatus().name(),
                op.getTotalCount(),
                op.getProcessedCount(),
                op.getCreatedCount(),
                op.getRestoredCount(),
                op.getSkippedCount(),
                op.getFailedCount(),
                op.getErrorCode(),
                op.getExpiresAt(),
                op.getStartedAt(),
                op.getFinishedAt()
        );
    }
}
