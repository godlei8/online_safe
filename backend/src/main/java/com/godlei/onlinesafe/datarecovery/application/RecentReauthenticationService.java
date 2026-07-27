package com.godlei.onlinesafe.datarecovery.application;

import com.godlei.onlinesafe.audit.application.SecurityAuditService;
import com.godlei.onlinesafe.audit.domain.AuditActorType;
import com.godlei.onlinesafe.audit.domain.AuditEventType;
import com.godlei.onlinesafe.audit.domain.AuditResult;
import com.godlei.onlinesafe.auth.domain.AppUser;
import com.godlei.onlinesafe.auth.infrastructure.AppUserRepository;
import com.godlei.onlinesafe.security.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RecentReauthenticationService {

    public static final String SESSION_ATTR = "os.security.reauthenticatedAt";
    private static final Duration VALIDITY = Duration.ofMinutes(10);
    private static final Duration RATE_WINDOW = Duration.ofMinutes(15);
    private static final int MAX_ATTEMPTS = 5;

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditService securityAuditService;
    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Instant>> failureBuckets = new ConcurrentHashMap<>();

    public RecentReauthenticationService(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            SecurityAuditService securityAuditService,
            Clock clock
    ) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.securityAuditService = securityAuditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Instant reauthenticate(AppUserPrincipal principal, String password, HttpServletRequest request) {
        String userId = principal.userId();
        String rateKey = userId + "|" + clientHint(request);
        if (isRateLimited(rateKey)) {
            throw new DataRecoveryException("REAUTH_RATE_LIMITED", "验证尝试过于频繁，请稍后再试");
        }
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new DataRecoveryException("REAUTH_FAILED", "登录密码不正确"));
        if (password == null || password.isBlank() || !passwordEncoder.matches(password, user.getPasswordHash())) {
            recordFailure(rateKey);
            securityAuditService.recordInTx(
                    AuditEventType.USER_REAUTH_FAILED,
                    AuditResult.FAILED,
                    AuditActorType.USER,
                    userId,
                    principal.username(),
                    null,
                    null,
                    null,
                    "REAUTH_FAILED",
                    Map.of("reason", "PASSWORD_MISMATCH")
            );
            throw new DataRecoveryException("REAUTH_FAILED", "登录密码不正确");
        }
        failureBuckets.remove(rateKey);
        Instant now = clock.instant();
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new DataRecoveryException("REAUTH_FAILED", "登录密码不正确");
        }
        session.setAttribute(SESSION_ATTR, now.toString());
        securityAuditService.recordInTx(
                AuditEventType.USER_REAUTH_SUCCEEDED,
                AuditResult.SUCCESS,
                AuditActorType.USER,
                userId,
                principal.username(),
                null,
                null,
                null,
                null,
                Map.of()
        );
        return now.plus(VALIDITY);
    }

    public void requireRecent(HttpServletRequest request) {
        if (!isRecent(request)) {
            throw new DataRecoveryException("REAUTH_REQUIRED", "请再次验证登录密码");
        }
    }

    public boolean isRecent(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        Object raw = session.getAttribute(SESSION_ATTR);
        if (!(raw instanceof String text) || text.isBlank()) {
            return false;
        }
        try {
            Instant at = Instant.parse(text);
            return !clock.instant().isAfter(at.plus(VALIDITY));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public void clear(HttpSession session) {
        if (session != null) {
            session.removeAttribute(SESSION_ATTR);
        }
    }

    private boolean isRateLimited(String key) {
        prune(key);
        Deque<Instant> deque = failureBuckets.get(key);
        return deque != null && deque.size() >= MAX_ATTEMPTS;
    }

    private void recordFailure(String key) {
        Instant now = clock.instant();
        failureBuckets.compute(key, (ignored, existing) -> {
            Deque<Instant> deque = existing == null ? new ArrayDeque<>() : existing;
            while (!deque.isEmpty() && deque.peekFirst().isBefore(now.minus(RATE_WINDOW))) {
                deque.removeFirst();
            }
            deque.addLast(now);
            return deque;
        });
    }

    private void prune(String key) {
        Instant cutoff = clock.instant().minus(RATE_WINDOW);
        failureBuckets.computeIfPresent(key, (ignored, deque) -> {
            while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
                deque.removeFirst();
            }
            return deque.isEmpty() ? null : deque;
        });
    }

    private static String clientHint(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }
}
