package com.godlei.onlinesafe.vaultimport.application;

import com.godlei.onlinesafe.audit.application.SecurityAuditService;
import com.godlei.onlinesafe.audit.domain.AuditActorType;
import com.godlei.onlinesafe.audit.domain.AuditEventType;
import com.godlei.onlinesafe.audit.domain.AuditResult;
import com.godlei.onlinesafe.datarecovery.application.RecentReauthenticationService;
import com.godlei.onlinesafe.settings.application.SystemSettingService;
import com.godlei.onlinesafe.vault.application.InvalidVaultEnvelopeException;
import com.godlei.onlinesafe.vault.application.VaultItemService;
import com.godlei.onlinesafe.vault.web.VaultRecordRequest;
import com.godlei.onlinesafe.vaultimport.domain.ImportCandidateBucket;
import com.godlei.onlinesafe.vaultimport.domain.ImportFileFormat;
import com.godlei.onlinesafe.vaultimport.domain.ImportIssueCode;
import com.godlei.onlinesafe.vaultimport.domain.ImportMode;
import com.godlei.onlinesafe.vaultimport.domain.ImportSessionStatus;
import com.godlei.onlinesafe.vaultimport.domain.VaultImportSession;
import com.godlei.onlinesafe.vaultimport.infrastructure.VaultImportSessionRepository;
import com.godlei.onlinesafe.vaultimport.web.ImportCandidatePatchRequest;
import com.godlei.onlinesafe.vaultimport.web.ImportCommitRequest;
import com.godlei.onlinesafe.vaultimport.web.ImportCommitResponse;
import com.godlei.onlinesafe.vaultimport.web.ImportSessionResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class VaultImportService {

    private static final Logger log = LoggerFactory.getLogger(VaultImportService.class);

    private final VaultImportSessionRepository repository;
    private final ImportFileExtractor fileExtractor;
    private final ImportAiMapper aiMapper;
    private final ImportRuleEngine ruleEngine;
    private final ImportCandidateSealService sealService;
    private final SystemSettingService systemSettingService;
    private final RecentReauthenticationService reauthenticationService;
    private final VaultItemService vaultItemService;
    private final SecurityAuditService auditService;
    private final Clock clock;
    private final VaultImportAsyncRunner asyncRunner;

    public VaultImportService(
            VaultImportSessionRepository repository,
            ImportFileExtractor fileExtractor,
            ImportAiMapper aiMapper,
            ImportRuleEngine ruleEngine,
            ImportCandidateSealService sealService,
            SystemSettingService systemSettingService,
            RecentReauthenticationService reauthenticationService,
            VaultItemService vaultItemService,
            SecurityAuditService auditService,
            Clock clock,
            @Lazy VaultImportAsyncRunner asyncRunner
    ) {
        this.repository = repository;
        this.fileExtractor = fileExtractor;
        this.aiMapper = aiMapper;
        this.ruleEngine = ruleEngine;
        this.sealService = sealService;
        this.systemSettingService = systemSettingService;
        this.reauthenticationService = reauthenticationService;
        this.vaultItemService = vaultItemService;
        this.auditService = auditService;
        this.clock = clock;
        this.asyncRunner = asyncRunner;
    }

    @Transactional
    public ImportSessionResponse createSession(String ownerId, MultipartFile file, ImportMode mode) {
        requireEnabled();
        ImportMode effectiveMode = mode == null ? ImportMode.FAST : mode;
        if (file == null || file.isEmpty()) {
            throw new VaultImportException("IMPORT_FILE_TYPE_UNSUPPORTED", "请选择要导入的文件");
        }
        long maxBytes = systemSettingService.smartImportMaxFileBytes();
        if (file.getSize() > maxBytes) {
            throw new VaultImportException("IMPORT_FILE_TOO_LARGE", "文件过大，最大允许 " + maxBytes + " 字节");
        }
        String fileName = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        ImportFileFormat format = fileExtractor.detectFormat(fileName);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new VaultImportException("IMPORT_FILE_TYPE_UNSUPPORTED", "无法读取上传文件");
        }

        Instant now = clock.instant();
        VaultImportSession session = VaultImportSession.create(
                UUID.randomUUID().toString(),
                ownerId,
                fileName,
                format,
                file.getSize(),
                now.plus(Duration.ofMinutes(systemSettingService.smartImportSessionTtlMinutes()))
        );
        session.markParsing(
                effectiveMode == ImportMode.FAST
                        ? "文件已接收，正在快速解析…"
                        : "文件已接收，正在解析…"
        );
        repository.saveAndFlush(session);
        audit(ownerId, AuditEventType.VAULT_IMPORT_CREATED, AuditResult.SUCCESS, session.getId(), null, Map.of(
                "sessionId", session.getId(),
                "fileFormat", format.name(),
                "byteSize", file.getSize(),
                "importMode", effectiveMode.name()
        ));
        // 必须在事务提交后再异步处理，否则工作线程读不到未提交的会话，会静默退出导致前端一直卡住
        String sessionId = session.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    asyncRunner.process(sessionId, ownerId, bytes, fileName, effectiveMode);
                }
            });
        } else {
            asyncRunner.process(sessionId, ownerId, bytes, fileName, effectiveMode);
        }
        return toResponse(session, List.of(), effectiveMode);
    }

    @Transactional
    public void processUploadedFile(String sessionId, String ownerId, byte[] bytes, String fileName, ImportMode mode) {
        ImportMode effectiveMode = mode == null ? ImportMode.FAST : mode;
        VaultImportSession session = repository.findByIdAndOwnerId(sessionId, ownerId).orElse(null);
        if (session == null) {
            log.warn("Import session {} not found for owner {}, skip async processing", sessionId, ownerId);
            return;
        }
        if (session.getStatus() == ImportSessionStatus.DISCARDED) {
            return;
        }
        try {
            session.markParsing(
                    effectiveMode == ImportMode.FAST ? "正在解析并按表头映射…" : "正在解析文件内容…"
            );
            repository.saveAndFlush(session);

            ImportDocument document = fileExtractor.extract(fileName, bytes, systemSettingService.smartImportMaxRows());
            if (isAbandoned(sessionId, ownerId)) {
                return;
            }
            session.updateProgress("已解析 " + document.rows().size() + " 行");
            repository.saveAndFlush(session);
            audit(ownerId, AuditEventType.VAULT_IMPORT_PARSED, AuditResult.SUCCESS, session.getId(), null, Map.of(
                    "sessionId", session.getId(),
                    "rowCount", document.rows().size(),
                    "fileFormat", session.getFileFormat().name(),
                    "importMode", effectiveMode.name()
            ));

            if (effectiveMode == ImportMode.AI) {
                session.markAiMapping(
                        document.rows().isEmpty()
                                ? "没有可识别的行"
                                : "正在智能识别字段（0/" + document.rows().size() + "）…"
                );
            } else {
                session.markAiMapping("正在按本地规则整理字段…");
            }
            repository.saveAndFlush(session);

            ImportAiMapper.MapResult mapped = aiMapper.map(document, effectiveMode, (done, total) -> {
                if (effectiveMode != ImportMode.AI) {
                    return;
                }
                VaultImportSession live = repository.findByIdAndOwnerId(sessionId, ownerId).orElse(null);
                if (live == null || live.getStatus() == ImportSessionStatus.DISCARDED) {
                    return;
                }
                live.markAiMapping("正在智能识别字段（" + done + "/" + total + "）…");
                repository.saveAndFlush(live);
            });
            if (isAbandoned(sessionId, ownerId)) {
                return;
            }

            session = repository.findByIdAndOwnerId(sessionId, ownerId).orElse(null);
            if (session == null || session.getStatus() == ImportSessionStatus.DISCARDED) {
                return;
            }
            session.updateProgress("正在归类候选记录…");
            repository.saveAndFlush(session);

            List<ImportCandidate> candidates = ruleEngine.classify(
                    ownerId,
                    mapped.candidates(),
                    systemSettingService.smartImportConfidenceThresholdPercent()
            );
            sealService.seal(session, candidates);
            Counts counts = countBuckets(candidates);
            session.markReady(
                    candidates.size(),
                    counts.ready(),
                    counts.needsReview(),
                    counts.skipped(),
                    mapped.provider(),
                    mapped.modelName()
            );
            repository.saveAndFlush(session);
            audit(ownerId, AuditEventType.VAULT_IMPORT_AI_DONE, AuditResult.SUCCESS, session.getId(), null, Map.of(
                    "sessionId", session.getId(),
                    "readyCount", counts.ready(),
                    "needsReviewCount", counts.needsReview(),
                    "skippedCount", counts.skipped(),
                    "modelName", mapped.modelName() == null ? "" : mapped.modelName(),
                    "degraded", mapped.degraded(),
                    "importMode", effectiveMode.name()
            ));
        } catch (VaultImportException exception) {
            failSession(sessionId, ownerId, exception.getCode(), exception.getMessage());
        } catch (RuntimeException exception) {
            log.warn("Import session {} failed", sessionId, exception);
            failSession(sessionId, ownerId, "IMPORT_FAILED", "导入解析失败，请稍后重试");
        }
    }

    private boolean isAbandoned(String sessionId, String ownerId) {
        return repository.findByIdAndOwnerId(sessionId, ownerId)
                .map(session -> session.getStatus() == ImportSessionStatus.DISCARDED)
                .orElse(true);
    }

    private void failSession(String sessionId, String ownerId, String errorCode, String message) {
        VaultImportSession session = repository.findByIdAndOwnerId(sessionId, ownerId).orElse(null);
        if (session == null || session.getStatus() == ImportSessionStatus.DISCARDED) {
            return;
        }
        session.markFailed(errorCode, message == null || message.isBlank() ? "导入失败" : message);
        repository.saveAndFlush(session);
        audit(ownerId, AuditEventType.VAULT_IMPORT_FAILED, AuditResult.FAILED, session.getId(), errorCode, Map.of(
                "sessionId", session.getId(),
                "errorCode", errorCode
        ));
    }

    @Transactional(readOnly = true)
    public ImportSessionResponse getSession(String ownerId, String sessionId) {
        VaultImportSession session = requireOwned(ownerId, sessionId);
        ensureNotExpired(session);
        return toResponse(session, sealService.unseal(session));
    }

    @Transactional
    public ImportSessionResponse patchCandidate(String ownerId, String sessionId, String candidateId, ImportCandidatePatchRequest request) {
        VaultImportSession session = requireOwned(ownerId, sessionId);
        ensureReady(session);
        List<ImportCandidate> candidates = sealService.unseal(session);
        ImportCandidate target = candidates.stream()
                .filter(item -> candidateId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new VaultImportException("IMPORT_SESSION_NOT_FOUND", "候选记录不存在"));
        if (request.payload() != null) {
            target.setPayload(request.payload());
        }
        if (request.duplicateAction() != null) {
            target.setDuplicateAction(ImportCandidate.DuplicateAction.valueOf(request.duplicateAction()));
        }
        if (request.confidence() != null) {
            target.setConfidence(request.confidence());
        }
        if (Boolean.TRUE.equals(request.forceReady())) {
            target.setForceReady(true);
            if (target.getConfidence() < 1.0) {
                target.setConfidence(1.0);
            }
            if (target.getIssues().contains(ImportIssueCode.DUPLICATE_SUSPECTED)
                    && target.getDuplicateAction() != ImportCandidate.DuplicateAction.CREATE) {
                target.setDuplicateAction(ImportCandidate.DuplicateAction.CREATE);
            }
            ensureImportablePayload(target);
        }
        ruleEngine.classify(ownerId, candidates, systemSettingService.smartImportConfidenceThresholdPercent());
        sealService.seal(session, candidates);
        Counts counts = countBuckets(candidates);
        session.updateCounts(counts.ready(), counts.needsReview(), counts.skipped());
        repository.saveAndFlush(session);
        return toResponse(session, candidates);
    }

    @Transactional
    public ImportCommitResponse commit(String ownerId, String sessionId, ImportCommitRequest request, HttpServletRequest servletRequest) {
        reauthenticationService.requireRecent(servletRequest);
        VaultImportSession session = requireOwned(ownerId, sessionId);
        ensureReady(session);
        List<ImportCandidate> candidates = sealService.unseal(session);
        Set<String> selected = request == null || request.candidateIds() == null || request.candidateIds().isEmpty()
                ? null
                : new HashSet<>(request.candidateIds());
        session.markCommitting();
        repository.saveAndFlush(session);

        int succeeded = 0;
        List<ImportCommitResponse.Failure> failures = new ArrayList<>();
        for (ImportCandidate candidate : candidates) {
            if (candidate.getBucket() != ImportCandidateBucket.READY) {
                continue;
            }
            if (selected != null && !selected.contains(candidate.getId())) {
                continue;
            }
            try {
                String id = UUID.randomUUID().toString();
                JsonNode payload = candidate.getPayload();
                vaultItemService.create(ownerId, new VaultRecordRequest(id, payload, 0L));
                succeeded++;
            } catch (RuntimeException exception) {
                String code = "COMMIT_ITEM_FAILED";
                if (exception instanceof InvalidVaultEnvelopeException envelope
                        && envelope.getCode() != null
                        && !envelope.getCode().isBlank()) {
                    code = envelope.getCode();
                }
                failures.add(new ImportCommitResponse.Failure(
                        candidate.getId(),
                        candidate.getRowIndex(),
                        code
                ));
            }
        }
        session.markCommitted();
        repository.saveAndFlush(session);
        audit(ownerId, AuditEventType.VAULT_IMPORT_COMMITTED, AuditResult.SUCCESS, session.getId(), null, Map.of(
                "sessionId", session.getId(),
                "succeededCount", succeeded,
                "failedCount", failures.size()
        ));
        return new ImportCommitResponse(session.getId(), succeeded, failures.size(), failures);
    }

    @Transactional
    public void discard(String ownerId, String sessionId) {
        VaultImportSession session = requireOwned(ownerId, sessionId);
        session.markDiscarded();
        repository.saveAndFlush(session);
        audit(ownerId, AuditEventType.VAULT_IMPORT_DISCARDED, AuditResult.SUCCESS, session.getId(), null, Map.of(
                "sessionId", session.getId()
        ));
    }

    private void requireEnabled() {
        if (!systemSettingService.smartImportEnabled()) {
            throw new VaultImportException("IMPORT_DISABLED", "智能导入已关闭");
        }
    }

    private VaultImportSession requireOwned(String ownerId, String sessionId) {
        return repository.findByIdAndOwnerId(sessionId, ownerId)
                .orElseThrow(() -> new VaultImportException("IMPORT_SESSION_NOT_FOUND", "导入会话不存在"));
    }

    private void ensureNotExpired(VaultImportSession session) {
        if (session.isExpired(clock.instant())
                || session.getStatus() == ImportSessionStatus.DISCARDED
                || session.getStatus() == ImportSessionStatus.COMMITTED) {
            throw new VaultImportException("IMPORT_SESSION_EXPIRED", "导入会话已过期或已结束");
        }
    }

    private void ensureReady(VaultImportSession session) {
        ensureNotExpired(session);
        if (session.getStatus() != ImportSessionStatus.READY) {
            throw new VaultImportException("IMPORT_SESSION_EXPIRED", "导入会话状态不可用");
        }
    }

    private ImportSessionResponse toResponse(VaultImportSession session, List<ImportCandidate> candidates) {
        return toResponse(session, candidates, ImportMode.from(recognitionModeOf(session)));
    }

    private ImportSessionResponse toResponse(
            VaultImportSession session,
            List<ImportCandidate> candidates,
            ImportMode mode
    ) {
        ImportMode effective = mode == null ? ImportMode.FAST : mode;
        return new ImportSessionResponse(
                session.getId(),
                session.getStatus().name(),
                progressMessageOf(session),
                session.getErrorCode(),
                effective.name(),
                session.getFileName(),
                session.getFileFormat().name(),
                session.getByteSize(),
                session.getRowCount(),
                session.getReadyCount(),
                session.getNeedsReviewCount(),
                session.getSkippedCount(),
                systemSettingService.smartImportConfidenceThresholdPercent() / 100.0,
                session.getModelName(),
                session.getModelProvider(),
                session.getExpiresAt(),
                candidates.stream().map(this::toCandidateResponse).toList()
        );
    }

    private static String recognitionModeOf(VaultImportSession session) {
        String provider = session.getModelProvider();
        if (provider != null && (provider.equalsIgnoreCase("fast") || provider.equalsIgnoreCase("deterministic"))) {
            return ImportMode.FAST.name();
        }
        if (session.getModelName() != null && session.getModelName().contains("本地")) {
            return ImportMode.FAST.name();
        }
        if (session.getModelName() != null || (provider != null && !provider.isBlank())) {
            return ImportMode.AI.name();
        }
        return ImportMode.FAST.name();
    }

    private static String progressMessageOf(VaultImportSession session) {
        if (session.getProgressMessage() != null && !session.getProgressMessage().isBlank()) {
            return session.getProgressMessage();
        }
        return switch (session.getStatus()) {
            case UPLOADING -> "正在接收文件…";
            case PARSING -> "正在解析文件…";
            case AI_MAPPING -> "正在智能识别字段…";
            case READY -> "识别完成，请核对结果";
            case COMMITTING -> "正在写入保险箱…";
            case COMMITTED -> "导入已完成";
            case FAILED -> "导入失败";
            case DISCARDED -> "会话已取消";
        };
    }

    private ImportSessionResponse.Candidate toCandidateResponse(ImportCandidate candidate) {
        return new ImportSessionResponse.Candidate(
                candidate.getId(),
                candidate.getRowIndex(),
                candidate.getConfidence(),
                candidate.getBucket().name(),
                candidate.getIssues().stream().map(Enum::name).toList(),
                candidate.getDuplicateAction() == null ? null : candidate.getDuplicateAction().name(),
                candidate.getPayload()
        );
    }

    private void ensureImportablePayload(ImportCandidate candidate) {
        JsonNode payload = candidate.getPayload();
        if (payload == null || !payload.isObject()) {
            return;
        }
        ObjectNode object = (ObjectNode) payload;
        String name = object.path("name").asText("").trim();
        if (name.isBlank() || "未命名".equals(name) || "未命名记录".equals(name)) {
            object.put("name", "导入记录");
        }
        String platform = object.path("platform").asText("").trim();
        if ("未填写".equals(platform)) {
            object.put("platform", "");
        }
        if (!object.path("fields").isArray()) {
            object.putArray("fields");
        }
        candidate.setPayload(object);
    }

    private Counts countBuckets(List<ImportCandidate> candidates) {
        int ready = 0;
        int needs = 0;
        int skipped = 0;
        for (ImportCandidate candidate : candidates) {
            switch (candidate.getBucket()) {
                case READY -> ready++;
                case NEEDS_REVIEW -> needs++;
                case SKIPPED -> skipped++;
            }
        }
        return new Counts(ready, needs, skipped);
    }

    private void audit(
            String ownerId,
            AuditEventType type,
            AuditResult result,
            String sessionId,
            String errorCode,
            Map<String, Object> metadata
    ) {
        auditService.recordInTx(
                type,
                result,
                AuditActorType.USER,
                ownerId,
                null,
                "IMPORT_SESSION",
                sessionId,
                null,
                errorCode,
                new HashMap<>(metadata)
        );
    }

    private record Counts(int ready, int needsReview, int skipped) {
    }
}
