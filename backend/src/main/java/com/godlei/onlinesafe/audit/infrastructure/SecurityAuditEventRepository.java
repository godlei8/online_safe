package com.godlei.onlinesafe.audit.infrastructure;

import com.godlei.onlinesafe.audit.domain.AuditActorType;
import com.godlei.onlinesafe.audit.domain.AuditCategory;
import com.godlei.onlinesafe.audit.domain.AuditResult;
import com.godlei.onlinesafe.audit.domain.AuditRiskLevel;
import com.godlei.onlinesafe.audit.domain.SecurityAuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface SecurityAuditEventRepository
        extends JpaRepository<SecurityAuditEvent, String>, JpaSpecificationExecutor<SecurityAuditEvent> {

    long countByOccurredAtGreaterThanEqualAndResult(Instant from, AuditResult result);

    long countByOccurredAtGreaterThanEqualAndRiskLevel(Instant from, AuditRiskLevel riskLevel);

    long countByOccurredAtGreaterThanEqualAndActorType(Instant from, AuditActorType actorType);

    long countByOccurredAtGreaterThanEqualAndCategory(Instant from, AuditCategory category);

    Page<SecurityAuditEvent> findByOccurredAtBefore(Instant cutoff, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SecurityAuditEvent e where e.id in :ids")
    int deleteByIdIn(@Param("ids") java.util.Collection<String> ids);
}
