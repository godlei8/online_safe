package com.godlei.onlinesafe.audit.web;

import com.godlei.onlinesafe.audit.application.SecurityAuditQueryService;
import com.godlei.onlinesafe.audit.domain.AuditActorType;
import com.godlei.onlinesafe.audit.domain.AuditCategory;
import com.godlei.onlinesafe.audit.domain.AuditResult;
import com.godlei.onlinesafe.audit.domain.AuditRiskLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/v1/security-logs")
public class SecurityAuditAdminController {

    private final SecurityAuditQueryService queryService;

    public SecurityAuditAdminController(SecurityAuditQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public Page<SecurityAuditListItemResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) AuditCategory category,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) AuditRiskLevel riskLevel,
            @RequestParam(required = false) AuditResult result,
            @RequestParam(required = false) AuditActorType actorType,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        return queryService.list(
                from,
                to,
                category,
                eventType,
                riskLevel,
                result,
                actorType,
                q,
                PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "occurredAt"))
        );
    }

    @GetMapping("/stats")
    public SecurityAuditStatsResponse stats() {
        return queryService.stats();
    }

    @GetMapping("/event-types")
    public Map<String, Object> eventTypes() {
        return queryService.eventTypes();
    }

    @GetMapping("/{id}")
    public SecurityAuditDetailResponse detail(@PathVariable String id) {
        return queryService.detail(id);
    }
}
