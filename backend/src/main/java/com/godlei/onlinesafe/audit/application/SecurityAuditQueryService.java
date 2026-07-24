package com.godlei.onlinesafe.audit.application;

import com.godlei.onlinesafe.audit.domain.AuditActorType;
import com.godlei.onlinesafe.audit.domain.AuditCategory;
import com.godlei.onlinesafe.audit.domain.AuditEventType;
import com.godlei.onlinesafe.audit.domain.AuditResult;
import com.godlei.onlinesafe.audit.domain.AuditRiskLevel;
import com.godlei.onlinesafe.audit.domain.SecurityAuditEvent;
import com.godlei.onlinesafe.audit.infrastructure.SecurityAuditEventRepository;
import com.godlei.onlinesafe.audit.web.SecurityAuditDetailResponse;
import com.godlei.onlinesafe.audit.web.SecurityAuditListItemResponse;
import com.godlei.onlinesafe.audit.web.SecurityAuditStatsResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SecurityAuditQueryService {

    private static final Duration DEFAULT_RANGE = Duration.ofDays(7);
    private static final Duration MAX_RANGE = Duration.ofDays(180);

    private final SecurityAuditEventRepository repository;
    private final Clock clock;

    public SecurityAuditQueryService(SecurityAuditEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SecurityAuditStatsResponse stats() {
        Instant now = clock.instant();
        Instant last24h = now.minus(Duration.ofHours(24));
        Instant last7d = now.minus(Duration.ofDays(7));
        return new SecurityAuditStatsResponse(
                countLoginFailures(last24h),
                repository.countByOccurredAtGreaterThanEqualAndRiskLevel(last7d, AuditRiskLevel.HIGH),
                repository.countByOccurredAtGreaterThanEqualAndActorType(last7d, AuditActorType.ADMIN),
                repository.countByOccurredAtGreaterThanEqualAndResult(last24h, AuditResult.BLOCKED)
        );
    }

    private long countLoginFailures(Instant from) {
        return repository.count((root, query, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.get("occurredAt"), from),
                root.get("eventType").in(
                        AuditEventType.USER_LOGIN_FAILED.name(),
                        AuditEventType.ADMIN_LOGIN_FAILED.name()
                )
        ));
    }

    @Transactional(readOnly = true)
    public Page<SecurityAuditListItemResponse> list(
            Instant from,
            Instant to,
            AuditCategory category,
            String eventType,
            AuditRiskLevel riskLevel,
            AuditResult result,
            AuditActorType actorType,
            String q,
            Pageable pageable
    ) {
        InstantRange range = resolveRange(from, to);
        if (q != null && q.trim().length() > 64) {
            throw new AuditQueryException(HttpStatus.BAD_REQUEST, "INVALID_QUERY", "搜索关键字最长 64 字符");
        }
        Specification<SecurityAuditEvent> spec = buildSpec(
                range.from(), range.to(), category, eventType, riskLevel, result, actorType, q
        );
        return repository.findAll(spec, pageable).map(this::toListItem);
    }

    @Transactional(readOnly = true)
    public SecurityAuditDetailResponse detail(String id) {
        SecurityAuditEvent event = repository.findById(id)
                .orElseThrow(() -> new AuditQueryException(HttpStatus.NOT_FOUND, "AUDIT_EVENT_NOT_FOUND", "日志不存在"));
        return toDetail(event);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> eventTypes() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("events", AuditEventType.dictionary());
        payload.put("categories", enumOptions(AuditCategory.values()));
        payload.put("results", enumOptions(AuditResult.values()));
        payload.put("riskLevels", enumOptions(AuditRiskLevel.values()));
        payload.put("actorTypes", enumOptions(AuditActorType.values()));
        return payload;
    }

    private InstantRange resolveRange(Instant from, Instant to) {
        Instant now = clock.instant();
        Instant effectiveTo = to == null ? now : to;
        Instant effectiveFrom = from == null ? effectiveTo.minus(DEFAULT_RANGE) : from;
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new AuditQueryException(HttpStatus.BAD_REQUEST, "INVALID_TIME_RANGE", "开始时间不能晚于结束时间");
        }
        if (Duration.between(effectiveFrom, effectiveTo).compareTo(MAX_RANGE) > 0) {
            throw new AuditQueryException(HttpStatus.BAD_REQUEST, "INVALID_TIME_RANGE", "单次查询跨度不能超过 180 天");
        }
        return new InstantRange(effectiveFrom, effectiveTo);
    }

    private Specification<SecurityAuditEvent> buildSpec(
            Instant from,
            Instant to,
            AuditCategory category,
            String eventType,
            AuditRiskLevel riskLevel,
            AuditResult result,
            AuditActorType actorType,
            String q
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to));
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (eventType != null && !eventType.isBlank()) {
                predicates.add(cb.equal(root.get("eventType"), eventType.trim()));
            }
            if (riskLevel != null) {
                predicates.add(cb.equal(root.get("riskLevel"), riskLevel));
            }
            if (result != null) {
                predicates.add(cb.equal(root.get("result"), result));
            }
            if (actorType != null) {
                predicates.add(cb.equal(root.get("actorType"), actorType));
            }
            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("id")), pattern),
                        cb.like(cb.lower(root.get("actorLabelSnapshot")), pattern),
                        cb.like(cb.lower(root.get("targetLabelSnapshot")), pattern),
                        cb.like(cb.lower(root.get("identifierHint")), pattern)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private SecurityAuditListItemResponse toListItem(SecurityAuditEvent event) {
        AuditEventType type = AuditEventType.fromCode(event.getEventType()).orElse(null);
        return new SecurityAuditListItemResponse(
                event.getId(),
                event.getOccurredAt(),
                event.getCategory().name(),
                event.getEventType(),
                type == null ? event.getEventType() : type.labelZh(),
                event.getRiskLevel().name(),
                event.getResult().name(),
                event.getActorType().name(),
                displayActor(event),
                displayTarget(event),
                sourceSummary(event),
                event.getOccurrenceCount()
        );
    }

    private SecurityAuditDetailResponse toDetail(SecurityAuditEvent event) {
        AuditEventType type = AuditEventType.fromCode(event.getEventType()).orElse(null);
        return new SecurityAuditDetailResponse(
                event.getId(),
                event.getOccurredAt(),
                event.getCategory().name(),
                event.getEventType(),
                type == null ? event.getEventType() : type.labelZh(),
                event.getRiskLevel().name(),
                event.getResult().name(),
                event.getActorType().name(),
                displayActor(event),
                event.getActorId(),
                displayTarget(event),
                event.getTargetType(),
                event.getTargetId(),
                event.getErrorCode(),
                event.getRequestId(),
                event.getRouteTemplate(),
                event.getHttpMethod(),
                event.getIpMasked(),
                event.getBrowserFamily(),
                event.getOsFamily(),
                event.getDeviceType(),
                event.getOccurrenceCount(),
                event.getMetadataJson()
        );
    }

    private static String displayActor(SecurityAuditEvent event) {
        if (event.getActorLabelSnapshot() != null && !event.getActorLabelSnapshot().isBlank()) {
            return event.getActorLabelSnapshot();
        }
        if (event.getIdentifierHint() != null && !event.getIdentifierHint().isBlank()) {
            return event.getIdentifierHint();
        }
        return event.getActorType().name();
    }

    private static String displayTarget(SecurityAuditEvent event) {
        if (event.getTargetLabelSnapshot() != null && !event.getTargetLabelSnapshot().isBlank()) {
            return event.getTargetLabelSnapshot();
        }
        if (event.getTargetId() != null) {
            return event.getTargetId();
        }
        return "—";
    }

    private static String sourceSummary(SecurityAuditEvent event) {
        String ip = event.getIpMasked() == null ? "—" : event.getIpMasked();
        String browser = event.getBrowserFamily() == null ? "Unknown" : event.getBrowserFamily();
        String os = event.getOsFamily() == null ? "Unknown" : event.getOsFamily();
        return ip + " · " + browser + "/" + os;
    }

    private static List<Map<String, String>> enumOptions(Enum<?>[] values) {
        List<Map<String, String>> list = new ArrayList<>();
        for (Enum<?> value : values) {
            list.add(Map.of("value", value.name(), "label", value.name()));
        }
        return list;
    }

    private record InstantRange(Instant from, Instant to) {
    }
}
