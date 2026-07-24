package com.godlei.onlinesafe.session.web;

import com.godlei.onlinesafe.audit.application.SecurityAuditService;
import com.godlei.onlinesafe.audit.domain.AuditActorType;
import com.godlei.onlinesafe.audit.domain.AuditEventType;
import com.godlei.onlinesafe.audit.domain.AuditResult;
import com.godlei.onlinesafe.security.AppUserPrincipal;
import com.godlei.onlinesafe.session.application.UserSessionService;
import com.godlei.onlinesafe.session.domain.UserSessionView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.web.http.HttpSessionIdResolver;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/security/sessions")
public class UserSessionController {

    private final UserSessionService userSessionService;
    private final SecurityAuditService securityAuditService;
    private final HttpSessionIdResolver httpSessionIdResolver;

    public UserSessionController(
            UserSessionService userSessionService,
            SecurityAuditService securityAuditService,
            HttpSessionIdResolver httpSessionIdResolver
    ) {
        this.userSessionService = userSessionService;
        this.securityAuditService = securityAuditService;
        this.httpSessionIdResolver = httpSessionIdResolver;
    }

    @GetMapping
    public UserSessionListResponse list(
            @AuthenticationPrincipal AppUserPrincipal principal,
            HttpServletRequest request
    ) {
        String currentId = currentSessionId(request);
        List<UserSessionView> sessions = userSessionService.listActiveForUser(principal.getUsername(), currentId);
        return new UserSessionListResponse(
                sessions.size(),
                userSessionService.maxActiveSessions(),
                sessions.stream().map(UserSessionResponse::from).toList()
        );
    }

    @DeleteMapping("/{publicSessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeOne(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable String publicSessionId,
            HttpServletRequest request
    ) {
        String currentId = currentSessionId(request);
        boolean revoked = userSessionService.revokeOtherByPublicId(principal.getUsername(), currentId, publicSessionId);
        if (revoked) {
            securityAuditService.recordInTx(
                    AuditEventType.USER_SESSION_REVOKED,
                    AuditResult.SUCCESS,
                    AuditActorType.USER,
                    principal.userId(),
                    principal.username(),
                    "USER",
                    principal.userId(),
                    principal.username(),
                    null,
                    Map.of("sessionsRevoked", 1)
            );
        }
    }

    @PostMapping("/revoke-others")
    public RevokeOthersResponse revokeOthers(
            @AuthenticationPrincipal AppUserPrincipal principal,
            HttpServletRequest request
    ) {
        String currentId = currentSessionId(request);
        int revoked = userSessionService.revokeOthers(principal.getUsername(), currentId);
        if (revoked > 0) {
            securityAuditService.recordInTx(
                    AuditEventType.USER_OTHER_SESSIONS_REVOKED,
                    AuditResult.SUCCESS,
                    AuditActorType.USER,
                    principal.userId(),
                    principal.username(),
                    "USER",
                    principal.userId(),
                    principal.username(),
                    null,
                    Map.of("sessionsRevoked", revoked, "reason", "USER_REQUEST")
            );
        }
        return new RevokeOthersResponse(revoked);
    }

    @PostMapping("/revoke-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeAll(
            @AuthenticationPrincipal AppUserPrincipal principal,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String userId = principal.userId();
        String username = principal.username();
        int revoked = userSessionService.revokeAll(username);
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        httpSessionIdResolver.expireSession(request, response);
        securityAuditService.recordSessionRevokeAllIndependent(userId, username, revoked);
    }

    private static String currentSessionId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : session.getId();
    }
}
