package com.godlei.onlinesafe.datarecovery.application;

import com.godlei.onlinesafe.audit.application.SecurityAuditRecorder;
import com.godlei.onlinesafe.audit.application.SecurityAuditService;
import com.godlei.onlinesafe.audit.domain.AuditEventType;
import com.godlei.onlinesafe.audit.domain.AuditResult;
import com.godlei.onlinesafe.audit.domain.AuditRiskLevel;
import com.godlei.onlinesafe.audit.domain.SecurityAuditEvent;
import com.godlei.onlinesafe.settings.application.SystemSettingService;
import com.godlei.onlinesafe.vault.domain.PrivateTemplate;
import com.godlei.onlinesafe.vault.domain.VaultItem;
import com.godlei.onlinesafe.vault.infrastructure.PrivateTemplateRepository;
import com.godlei.onlinesafe.vault.infrastructure.VaultItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Component
public class VaultTrashRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(VaultTrashRetentionJob.class);
    private static final int BATCH_SIZE = 500;

    private final VaultItemRepository itemRepository;
    private final PrivateTemplateRepository templateRepository;
    private final SystemSettingService systemSettingService;
    private final SecurityAuditRecorder recorder;
    private final SecurityAuditService auditService;
    private final Clock clock;

    public VaultTrashRetentionJob(
            VaultItemRepository itemRepository,
            PrivateTemplateRepository templateRepository,
            SystemSettingService systemSettingService,
            SecurityAuditRecorder recorder,
            SecurityAuditService auditService,
            Clock clock
    ) {
        this.itemRepository = itemRepository;
        this.templateRepository = templateRepository;
        this.systemSettingService = systemSettingService;
        this.recorder = recorder;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Scheduled(cron = "0 10 4 * * *")
    public void purge() {
        Integer retentionDays;
        try {
            retentionDays = systemSettingService.recycleBinRetentionDaysOrNull();
        } catch (RuntimeException exception) {
            log.error("读取回收站保留期限失败，跳过清理");
            return;
        }
        if (retentionDays == null) {
            log.error("回收站保留期限不可用，跳过清理");
            return;
        }
        Instant cutoff = clock.instant().minus(retentionDays, ChronoUnit.DAYS);
        int totalDeleted = 0;
        try {
            while (true) {
                int deleted = purgeBatch(cutoff);
                if (deleted <= 0) {
                    break;
                }
                totalDeleted += deleted;
                if (deleted < BATCH_SIZE) {
                    break;
                }
            }
            SecurityAuditEvent event = auditService.buildSystemEvent(
                    AuditEventType.VAULT_TRASH_PURGE_SUCCEEDED,
                    AuditResult.SUCCESS,
                    AuditRiskLevel.INFO,
                    Map.of("deletedCount", totalDeleted, "cutoffAt", cutoff.toString()),
                    null
            );
            recorder.recordIndependent(event);
            log.info("回收站定时清理完成 deletedCount={} cutoffAt={}", totalDeleted, cutoff);
        } catch (RuntimeException exception) {
            log.error("回收站定时清理失败", exception);
            try {
                SecurityAuditEvent failed = auditService.buildSystemEvent(
                        AuditEventType.VAULT_TRASH_PURGE_FAILED,
                        AuditResult.FAILED,
                        AuditRiskLevel.HIGH,
                        Map.of("errorCode", "PURGE_FAILED"),
                        "PURGE_FAILED"
                );
                recorder.recordIndependent(failed);
            } catch (RuntimeException ignored) {
                // ignore
            }
        }
    }

    @Transactional
    int purgeBatch(Instant cutoff) {
        List<VaultItem> items = itemRepository.findExpiredTrash(cutoff, PageRequest.of(0, BATCH_SIZE));
        int deleted = 0;
        if (!items.isEmpty()) {
            itemRepository.deleteAllInBatch(items);
            deleted += items.size();
        }
        if (deleted < BATCH_SIZE) {
            List<PrivateTemplate> templates = templateRepository.findExpiredTrash(
                    cutoff, PageRequest.of(0, BATCH_SIZE - deleted));
            if (!templates.isEmpty()) {
                templateRepository.deleteAllInBatch(templates);
                deleted += templates.size();
            }
        }
        return deleted;
    }
}
