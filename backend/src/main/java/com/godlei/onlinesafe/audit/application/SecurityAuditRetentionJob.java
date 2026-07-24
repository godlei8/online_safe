package com.godlei.onlinesafe.audit.application;

import com.godlei.onlinesafe.audit.domain.AuditEventType;
import com.godlei.onlinesafe.audit.domain.AuditResult;
import com.godlei.onlinesafe.audit.domain.AuditRiskLevel;
import com.godlei.onlinesafe.audit.domain.SecurityAuditEvent;
import com.godlei.onlinesafe.settings.application.SystemSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Component
public class SecurityAuditRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditRetentionJob.class);

    private final SecurityAuditPurgeService purgeService;
    private final SecurityAuditRecorder recorder;
    private final SecurityAuditService auditService;
    private final SystemSettingService systemSettingService;
    private final Clock clock;

    public SecurityAuditRetentionJob(
            SecurityAuditPurgeService purgeService,
            SecurityAuditRecorder recorder,
            SecurityAuditService auditService,
            SystemSettingService systemSettingService,
            Clock clock
    ) {
        this.purgeService = purgeService;
        this.recorder = recorder;
        this.auditService = auditService;
        this.systemSettingService = systemSettingService;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void purge() {
        Integer retentionDays;
        try {
            retentionDays = systemSettingService.auditRetentionDaysOrNull();
        } catch (RuntimeException exception) {
            log.error("读取安全日志保留期限失败，跳过清理");
            return;
        }
        if (retentionDays == null) {
            log.error("安全日志保留期限不可用，跳过清理");
            return;
        }
        Instant cutoff = clock.instant().minus(retentionDays, ChronoUnit.DAYS);
        int totalDeleted = 0;
        try {
            while (true) {
                int deleted = purgeService.deleteBatch(cutoff);
                if (deleted <= 0) {
                    break;
                }
                totalDeleted += deleted;
            }
            SecurityAuditEvent event = auditService.buildSystemEvent(
                    AuditEventType.SECURITY_LOG_PURGE_SUCCEEDED,
                    AuditResult.SUCCESS,
                    AuditRiskLevel.INFO,
                    Map.of("deletedCount", totalDeleted, "cutoffAt", cutoff.toString()),
                    null
            );
            recorder.recordIndependent(event);
        } catch (RuntimeException exception) {
            log.error("安全日志清理失败", exception);
            try {
                SecurityAuditEvent failed = auditService.buildSystemEvent(
                        AuditEventType.SECURITY_LOG_PURGE_FAILED,
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
}
