package com.godlei.onlinesafe.vaultimport.application;

import com.godlei.onlinesafe.vaultimport.infrastructure.VaultImportSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class VaultImportRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(VaultImportRetentionJob.class);

    private final VaultImportSessionRepository repository;
    private final Clock clock;

    public VaultImportRetentionJob(VaultImportSessionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Scheduled(cron = "0 25 * * * *")
    @Transactional
    public void purgeExpired() {
        Instant now = clock.instant();
        int deleted = repository.deleteExpiredOrStale(now, now.minus(Duration.ofDays(1)));
        if (deleted > 0) {
            log.info("已清理过期智能导入会话 {} 条", deleted);
        }
    }
}
