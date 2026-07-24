package com.godlei.onlinesafe.audit.application;

import com.godlei.onlinesafe.audit.domain.SecurityAuditEvent;
import com.godlei.onlinesafe.audit.infrastructure.AuditProperties;
import com.godlei.onlinesafe.audit.infrastructure.SecurityAuditEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class SecurityAuditPurgeService {

    private final SecurityAuditEventRepository repository;
    private final AuditProperties auditProperties;

    public SecurityAuditPurgeService(SecurityAuditEventRepository repository, AuditProperties auditProperties) {
        this.repository = repository;
        this.auditProperties = auditProperties;
    }

    @Transactional
    public int deleteBatch(Instant cutoff) {
        Page<SecurityAuditEvent> page = repository.findByOccurredAtBefore(
                cutoff,
                PageRequest.of(0, auditProperties.purgeBatchSize(), Sort.by(Sort.Direction.ASC, "occurredAt"))
        );
        if (page.isEmpty()) {
            return 0;
        }
        List<String> ids = page.getContent().stream().map(SecurityAuditEvent::getId).toList();
        return repository.deleteByIdIn(ids);
    }
}
