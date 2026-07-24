package com.godlei.onlinesafe.audit.application;

import com.godlei.onlinesafe.audit.domain.SecurityAuditEvent;
import com.godlei.onlinesafe.audit.infrastructure.SecurityAuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultSecurityAuditRecorder implements SecurityAuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(DefaultSecurityAuditRecorder.class);

    private final SecurityAuditEventRepository repository;

    public DefaultSecurityAuditRecorder(SecurityAuditEventRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void record(SecurityAuditEvent event) {
        repository.save(event);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependent(SecurityAuditEvent event) {
        try {
            repository.saveAndFlush(event);
        } catch (RuntimeException exception) {
            log.error(
                    "独立审计写入失败 eventType={} result={} errorCode={}",
                    event.getEventType(),
                    event.getResult(),
                    event.getErrorCode()
            );
        }
    }
}
