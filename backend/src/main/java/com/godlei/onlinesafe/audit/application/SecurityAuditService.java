package com.godlei.onlinesafe.audit.application;

import com.godlei.onlinesafe.audit.domain.AuditActorType;
import com.godlei.onlinesafe.audit.domain.AuditEventType;
import com.godlei.onlinesafe.audit.domain.AuditResult;
import com.godlei.onlinesafe.audit.domain.AuditRiskLevel;
import com.godlei.onlinesafe.audit.domain.SecurityAuditEvent;
import com.godlei.onlinesafe.audit.infrastructure.AuditFingerprintService;
import com.godlei.onlinesafe.audit.infrastructure.ClientContext;
import com.godlei.onlinesafe.audit.infrastructure.ClientContextResolver;
import com.godlei.onlinesafe.security.AdminUserPrincipal;
import com.godlei.onlinesafe.security.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.util.Map;

@Service
public class SecurityAuditService {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditService.class);

    private final SecurityAuditRecorder recorder;
    private final ClientContextResolver clientContextResolver;
    private final AuditFingerprintService fingerprintService;
    private final AuditMetadataPolicy metadataPolicy;
    private final IdentifierMasker identifierMasker;
    private final AuthFailureAggregator failureAggregator;
    private final Clock clock;

    public SecurityAuditService(
            SecurityAuditRecorder recorder,
            ClientContextResolver clientContextResolver,
            AuditFingerprintService fingerprintService,
            AuditMetadataPolicy metadataPolicy,
            IdentifierMasker identifierMasker,
            AuthFailureAggregator failureAggregator,
            Clock clock
    ) {
        this.recorder = recorder;
        this.clientContextResolver = clientContextResolver;
        this.fingerprintService = fingerprintService;
        this.metadataPolicy = metadataPolicy;
        this.identifierMasker = identifierMasker;
        this.failureAggregator = failureAggregator;
        this.clock = clock;
    }

    public void recordInTx(
            AuditEventType type,
            AuditResult result,
            AuditActorType actorType,
            String actorId,
            String actorLabel,
            String targetType,
            String targetId,
            String targetLabel,
            String errorCode,
            Map<String, Object> metadata
    ) {
        SecurityAuditEvent event = build(
                type, result, actorType, actorId, actorLabel,
                null, null, targetType, targetId, targetLabel, errorCode, metadata, 1, null
        );
        recorder.record(event);
    }

    public void recordAuthSuccessUser(String userId, String username, HttpServletRequest request) {
        SecurityAuditEvent event = build(
                AuditEventType.USER_LOGIN_SUCCEEDED,
                AuditResult.SUCCESS,
                AuditActorType.USER,
                userId,
                username,
                null,
                null,
                "USER",
                userId,
                username,
                null,
                null,
                1,
                request
        );
        recorder.record(event);
    }

    public void recordAuthSuccessAdmin(String adminId, String username, HttpServletRequest request) {
        SecurityAuditEvent event = build(
                AuditEventType.ADMIN_LOGIN_SUCCEEDED,
                AuditResult.SUCCESS,
                AuditActorType.ADMIN,
                adminId,
                username,
                null,
                null,
                "ADMIN",
                adminId,
                username,
                null,
                null,
                1,
                request
        );
        recorder.record(event);
    }

    public void recordAuthFailureUser(String identifier, HttpServletRequest request) {
        recordAuthFailure(AuditEventType.USER_LOGIN_FAILED, identifier, request, false);
    }

    public void recordAuthFailureAdmin(String identifier, HttpServletRequest request) {
        recordAuthFailure(AuditEventType.ADMIN_LOGIN_FAILED, identifier, request, true);
    }

    public void recordLogoutUser(Authentication authentication, HttpServletRequest request) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            return;
        }
        try {
            SecurityAuditEvent event = build(
                    AuditEventType.USER_LOGOUT_SUCCEEDED,
                    AuditResult.SUCCESS,
                    AuditActorType.USER,
                    principal.userId(),
                    principal.username(),
                    null,
                    null,
                    "USER",
                    principal.userId(),
                    principal.username(),
                    null,
                    null,
                    1,
                    request
            );
            recorder.recordIndependent(event);
        } catch (RuntimeException exception) {
            log.error("用户退出审计失败");
        }
    }

    public void recordLogoutAdmin(Authentication authentication, HttpServletRequest request) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AdminUserPrincipal principal)) {
            return;
        }
        try {
            SecurityAuditEvent event = build(
                    AuditEventType.ADMIN_LOGOUT_SUCCEEDED,
                    AuditResult.SUCCESS,
                    AuditActorType.ADMIN,
                    principal.adminId(),
                    principal.username(),
                    null,
                    null,
                    "ADMIN",
                    principal.adminId(),
                    principal.username(),
                    null,
                    null,
                    1,
                    request
            );
            recorder.recordIndependent(event);
        } catch (RuntimeException exception) {
            log.error("管理员退出审计失败");
        }
    }

    public void recordPasswordResetSuccess(String userId, String username) {
        recordInTx(
                AuditEventType.PASSWORD_RESET_SUCCEEDED,
                AuditResult.SUCCESS,
                AuditActorType.USER,
                userId,
                username,
                "USER",
                userId,
                username,
                null,
                null
        );
    }

    /** 退出全部设备后会话已失效，使用独立事务写审计。 */
    public void recordSessionRevokeAllIndependent(String userId, String username, int sessionsRevoked) {
        try {
            SecurityAuditEvent event = build(
                    AuditEventType.USER_ALL_SESSIONS_REVOKED,
                    AuditResult.SUCCESS,
                    AuditActorType.USER,
                    userId,
                    username,
                    null,
                    null,
                    "USER",
                    userId,
                    username,
                    null,
                    Map.of("sessionsRevoked", Math.max(sessionsRevoked, 0)),
                    1,
                    currentRequest()
            );
            recorder.recordIndependent(event);
        } catch (RuntimeException exception) {
            log.error("退出全部设备审计失败");
        }
    }

    public void recordSessionLimitReplaced(String userId, String username, int maxSessions, HttpServletRequest request) {
        try {
            SecurityAuditEvent event = build(
                    AuditEventType.USER_SESSION_LIMIT_REPLACED,
                    AuditResult.SUCCESS,
                    AuditActorType.USER,
                    userId,
                    username,
                    null,
                    null,
                    "USER",
                    userId,
                    username,
                    null,
                    Map.of("maxSessions", maxSessions),
                    1,
                    request
            );
            recorder.recordIndependent(event);
        } catch (RuntimeException exception) {
            log.error("会话上限替换审计失败");
        }
    }

    public void recordPasswordResetFailed(String phoneHint, String errorCode) {
        try {
            String hint = identifierMasker.maskLoginIdentifier(phoneHint);
            String hash = fingerprintService.hmacSha256Hex(normalize(phoneHint));
            SecurityAuditEvent event = build(
                    AuditEventType.PASSWORD_RESET_FAILED,
                    AuditResult.FAILED,
                    AuditActorType.ANONYMOUS,
                    null,
                    null,
                    hint,
                    hash,
                    null,
                    null,
                    null,
                    errorCode,
                    null,
                    1,
                    currentRequest()
            );
            recorder.recordIndependent(event);
        } catch (RuntimeException exception) {
            log.error("密码重置失败审计写入异常");
        }
    }

    public void recordSmsBlocked(String phone, String purpose, String errorCode) {
        try {
            String hint = identifierMasker.maskLoginIdentifier(phone);
            String hash = fingerprintService.hmacSha256Hex(normalize(phone));
            SecurityAuditEvent event = build(
                    AuditEventType.SMS_CODE_SEND_BLOCKED,
                    AuditResult.BLOCKED,
                    AuditActorType.ANONYMOUS,
                    null,
                    null,
                    hint,
                    hash,
                    null,
                    null,
                    null,
                    errorCode,
                    Map.of("purpose", purpose),
                    1,
                    currentRequest()
            );
            recorder.recordIndependent(event);
        } catch (RuntimeException exception) {
            log.error("短信限流审计写入异常");
        }
    }

    public void recordRegistrationSuccess(String userId, String username, boolean inviteUsed) {
        recordInTx(
                AuditEventType.USER_REGISTERED,
                AuditResult.SUCCESS,
                AuditActorType.USER,
                userId,
                username,
                "USER",
                userId,
                username,
                null,
                Map.of("inviteUsed", inviteUsed)
        );
    }

    public void recordRegistrationFailed(String identifier, String errorCode) {
        try {
            String hint = identifierMasker.maskLoginIdentifier(identifier);
            String hash = fingerprintService.hmacSha256Hex(normalize(identifier));
            SecurityAuditEvent event = build(
                    AuditEventType.USER_REGISTRATION_FAILED,
                    AuditResult.FAILED,
                    AuditActorType.ANONYMOUS,
                    null,
                    null,
                    hint,
                    hash,
                    null,
                    null,
                    null,
                    errorCode,
                    null,
                    1,
                    currentRequest()
            );
            recorder.recordIndependent(event);
        } catch (RuntimeException exception) {
            log.error("注册失败审计写入异常");
        }
    }

    public ActorSnapshot requireAdminActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AdminUserPrincipal principal) {
            return new ActorSnapshot(AuditActorType.ADMIN, principal.adminId(), principal.username());
        }
        throw new IllegalStateException("当前上下文不是管理员");
    }

    public ActorSnapshot currentActorOrAnonymous() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AdminUserPrincipal principal) {
            return new ActorSnapshot(AuditActorType.ADMIN, principal.adminId(), principal.username());
        }
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            return new ActorSnapshot(AuditActorType.USER, principal.userId(), principal.username());
        }
        return new ActorSnapshot(AuditActorType.ANONYMOUS, null, null);
    }

    private void recordAuthFailure(
            AuditEventType type,
            String identifier,
            HttpServletRequest request,
            boolean admin
    ) {
        try {
            ClientContext client = clientContextResolver.resolve(request);
            String hint = identifierMasker.maskLoginIdentifier(identifier);
            String idHash = fingerprintService.hmacSha256Hex(normalize(identifier));
            String key = (client.ipFingerprint() == null ? "none" : client.ipFingerprint())
                    + "|" + (idHash == null ? "none" : idHash)
                    + "|" + type.name();
            AuthFailureAggregator.Decision decision = failureAggregator.decide(key, admin);
            if (decision == AuthFailureAggregator.Decision.SKIP) {
                return;
            }
            if (decision == AuthFailureAggregator.Decision.WRITE_AGGREGATE) {
                int total = failureAggregator.totalInCurrentWindow(key);
                SecurityAuditEvent aggregate = build(
                        AuditEventType.AUTH_FAILURES_AGGREGATED,
                        AuditResult.BLOCKED,
                        AuditActorType.ANONYMOUS,
                        null,
                        null,
                        hint,
                        idHash,
                        null,
                        null,
                        null,
                        "AUTH_FAILURES_AGGREGATED",
                        Map.of("windowSeconds", 60),
                        Math.max(total, 1),
                        request
                );
                recorder.recordIndependent(aggregate);
                return;
            }
            SecurityAuditEvent event = build(
                    type,
                    AuditResult.FAILED,
                    AuditActorType.ANONYMOUS,
                    null,
                    null,
                    hint,
                    idHash,
                    null,
                    null,
                    null,
                    "BAD_CREDENTIALS",
                    null,
                    1,
                    request
            );
            recorder.recordIndependent(event);
        } catch (RuntimeException exception) {
            log.error("认证失败审计写入异常");
        }
    }

    private SecurityAuditEvent build(
            AuditEventType type,
            AuditResult result,
            AuditActorType actorType,
            String actorId,
            String actorLabel,
            String identifierHint,
            String identifierHash,
            String targetType,
            String targetId,
            String targetLabel,
            String errorCode,
            Map<String, Object> metadata,
            int occurrenceCount,
            HttpServletRequest request
    ) {
        HttpServletRequest effectiveRequest = request != null ? request : currentRequest();
        ClientContext client = clientContextResolver.resolve(effectiveRequest);
        Map<String, Object> cleanMeta = metadataPolicy.sanitize(type, metadata);
        SecurityAuditEvent.Builder builder = SecurityAuditEvent.builder(type, result)
                .occurredAt(clock.instant())
                .actor(actorType, actorId, actorLabel)
                .identifier(identifierHint, identifierHash)
                .target(targetType, targetId, targetLabel)
                .errorCode(errorCode)
                .request(client.requestId(), client.routeTemplate(), client.httpMethod())
                .client(
                        client.ipMasked(),
                        client.ipFingerprint(),
                        client.browserFamily(),
                        client.osFamily(),
                        client.deviceType()
                )
                .occurrenceCount(occurrenceCount)
                .metadata(cleanMeta);
        if (type == AuditEventType.SECURITY_LOG_RETENTION_CHANGED
                || (type == AuditEventType.SYSTEM_SETTINGS_UPDATED && metadata != null)) {
            // risk 已由事件字典默认；保留期限降低在设置服务中覆盖
        }
        return builder.build();
    }

    public SecurityAuditEvent buildSystemEvent(
            AuditEventType type,
            AuditResult result,
            AuditRiskLevel riskLevel,
            Map<String, Object> metadata,
            String errorCode
    ) {
        Map<String, Object> cleanMeta = metadataPolicy.sanitize(type, metadata);
        return SecurityAuditEvent.builder(type, result)
                .occurredAt(clock.instant())
                .riskLevel(riskLevel == null ? type.defaultRisk() : riskLevel)
                .actor(AuditActorType.SYSTEM, null, "SYSTEM")
                .errorCode(errorCode)
                .metadata(cleanMeta)
                .build();
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest();
        }
        return null;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    public record ActorSnapshot(AuditActorType type, String id, String label) {
    }
}
