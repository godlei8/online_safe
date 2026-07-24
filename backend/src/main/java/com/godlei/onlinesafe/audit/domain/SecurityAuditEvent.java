package com.godlei.onlinesafe.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "security_audit_event")
public class SecurityAuditEvent {

    @Id
    @Column(name = "id", nullable = false, length = 36, updatable = false)
    private String id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private AuditCategory category;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 16)
    private AuditRiskLevel riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 16)
    private AuditResult result;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 16)
    private AuditActorType actorType;

    @Column(name = "actor_id", length = 36)
    private String actorId;

    @Column(name = "actor_label_snapshot", length = 64)
    private String actorLabelSnapshot;

    @Column(name = "identifier_hint", length = 64)
    private String identifierHint;

    @Column(name = "identifier_hash", length = 64)
    private String identifierHash;

    @Column(name = "target_type", length = 32)
    private String targetType;

    @Column(name = "target_id", length = 64)
    private String targetId;

    @Column(name = "target_label_snapshot", length = 128)
    private String targetLabelSnapshot;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "route_template", length = 128)
    private String routeTemplate;

    @Column(name = "http_method", length = 8)
    private String httpMethod;

    @Column(name = "ip_masked", length = 64)
    private String ipMasked;

    @Column(name = "ip_fingerprint", length = 64)
    private String ipFingerprint;

    @Column(name = "browser_family", length = 32)
    private String browserFamily;

    @Column(name = "os_family", length = 32)
    private String osFamily;

    @Column(name = "device_type", length = 16)
    private String deviceType;

    @Column(name = "occurrence_count", nullable = false)
    private int occurrenceCount = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "json")
    private Map<String, Object> metadataJson;

    protected SecurityAuditEvent() {
    }

    public static Builder builder(AuditEventType type, AuditResult result) {
        return new Builder(type, result);
    }

    public String getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public AuditCategory getCategory() {
        return category;
    }

    public String getEventType() {
        return eventType;
    }

    public AuditRiskLevel getRiskLevel() {
        return riskLevel;
    }

    public AuditResult getResult() {
        return result;
    }

    public AuditActorType getActorType() {
        return actorType;
    }

    public String getActorId() {
        return actorId;
    }

    public String getActorLabelSnapshot() {
        return actorLabelSnapshot;
    }

    public String getIdentifierHint() {
        return identifierHint;
    }

    public String getIdentifierHash() {
        return identifierHash;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getTargetLabelSnapshot() {
        return targetLabelSnapshot;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getRouteTemplate() {
        return routeTemplate;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getIpMasked() {
        return ipMasked;
    }

    public String getIpFingerprint() {
        return ipFingerprint;
    }

    public String getBrowserFamily() {
        return browserFamily;
    }

    public String getOsFamily() {
        return osFamily;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public int getOccurrenceCount() {
        return occurrenceCount;
    }

    public Map<String, Object> getMetadataJson() {
        return metadataJson;
    }

    public static final class Builder {
        private final SecurityAuditEvent event = new SecurityAuditEvent();

        private Builder(AuditEventType type, AuditResult result) {
            event.id = UUID.randomUUID().toString();
            event.eventType = type.name();
            event.category = type.category();
            event.riskLevel = type.defaultRisk();
            event.result = result;
            event.actorType = AuditActorType.ANONYMOUS;
            event.occurrenceCount = 1;
        }

        public Builder occurredAt(Instant occurredAt) {
            event.occurredAt = occurredAt;
            return this;
        }

        public Builder riskLevel(AuditRiskLevel riskLevel) {
            event.riskLevel = riskLevel;
            return this;
        }

        public Builder actor(AuditActorType type, String id, String label) {
            event.actorType = type;
            event.actorId = id;
            event.actorLabelSnapshot = truncate(label, 64);
            return this;
        }

        public Builder identifier(String hint, String hash) {
            event.identifierHint = truncate(hint, 64);
            event.identifierHash = hash;
            return this;
        }

        public Builder target(String type, String id, String label) {
            event.targetType = truncate(type, 32);
            event.targetId = truncate(id, 64);
            event.targetLabelSnapshot = truncate(label, 128);
            return this;
        }

        public Builder errorCode(String errorCode) {
            event.errorCode = truncate(errorCode, 64);
            return this;
        }

        public Builder request(String requestId, String routeTemplate, String httpMethod) {
            event.requestId = truncate(requestId, 64);
            event.routeTemplate = truncate(routeTemplate, 128);
            event.httpMethod = truncate(httpMethod, 8);
            return this;
        }

        public Builder client(String ipMasked, String ipFingerprint, String browser, String os, String device) {
            event.ipMasked = truncate(ipMasked, 64);
            event.ipFingerprint = ipFingerprint;
            event.browserFamily = truncate(browser, 32);
            event.osFamily = truncate(os, 32);
            event.deviceType = truncate(device, 16);
            return this;
        }

        public Builder occurrenceCount(int count) {
            event.occurrenceCount = Math.max(1, count);
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            event.metadataJson = metadata;
            return this;
        }

        public SecurityAuditEvent build() {
            if (event.occurredAt == null) {
                event.occurredAt = Instant.now();
            }
            return event;
        }

        private static String truncate(String value, int max) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
        }
    }
}
