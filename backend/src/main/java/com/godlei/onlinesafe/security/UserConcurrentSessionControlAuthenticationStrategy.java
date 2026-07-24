package com.godlei.onlinesafe.security;

import com.godlei.onlinesafe.audit.application.SecurityAuditService;
import com.godlei.onlinesafe.session.application.SessionLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.authentication.session.ConcurrentSessionControlAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;

import java.util.List;

/**
 * 个人用户并发会话上限从系统设置动态读取，默认 2；超限时挤掉最久未用会话。
 */
public class UserConcurrentSessionControlAuthenticationStrategy extends ConcurrentSessionControlAuthenticationStrategy {

    private final SessionRegistry sessionRegistry;
    private final SessionLimitService sessionLimitService;
    private final SecurityAuditService securityAuditService;

    public UserConcurrentSessionControlAuthenticationStrategy(
            SessionRegistry sessionRegistry,
            SessionLimitService sessionLimitService,
            SecurityAuditService securityAuditService
    ) {
        super(sessionRegistry);
        this.sessionRegistry = sessionRegistry;
        this.sessionLimitService = sessionLimitService;
        this.securityAuditService = securityAuditService;
        setExceptionIfMaximumExceeded(false);
    }

    @Override
    protected int getMaximumSessionsForThisUser(Authentication authentication) {
        return sessionLimitService.maxActiveUserSessions();
    }

    @Override
    public void onAuthentication(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws SessionAuthenticationException {
        int max = getMaximumSessionsForThisUser(authentication);
        List<SessionInformation> existing = sessionRegistry.getAllSessions(authentication.getPrincipal(), false);
        boolean willReplace = existing.size() >= max;
        super.onAuthentication(authentication, request, response);
        if (willReplace && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            securityAuditService.recordSessionLimitReplaced(
                    principal.userId(),
                    principal.username(),
                    max,
                    request
            );
        }
    }
}
