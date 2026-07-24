package com.godlei.onlinesafe.security;

import com.godlei.onlinesafe.audit.application.SecurityAuditService;
import com.godlei.onlinesafe.session.application.SessionLimitService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.ConcurrentSessionControlAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;
import org.springframework.session.web.http.HttpSessionIdResolver;

import java.util.List;

@Configuration
public class SessionConfig {

    @Bean
    HttpSessionIdResolver httpSessionIdResolver(
            @Value("${server.servlet.session.cookie.secure:true}") boolean secure,
            @Value("${server.servlet.session.cookie.same-site:lax}") String sameSite
    ) {
        return new SurfaceAwareCookieHttpSessionIdResolver(secure, sameSite);
    }

    @Bean
    @SuppressWarnings("unchecked")
    SpringSessionBackedSessionRegistry<? extends Session> sessionRegistry(
            SessionRepository<? extends Session> sessionRepository
    ) {
        if (!(sessionRepository instanceof FindByIndexNameSessionRepository<?> indexed)) {
            throw new IllegalStateException("并发会话控制需要支持按主体索引的 SessionRepository");
        }
        return new SpringSessionBackedSessionRegistry<>((FindByIndexNameSessionRepository<Session>) indexed);
    }

    /**
     * 个人用户：动态上限（默认 2）→ 换 SessionId → 登记新会话。
     */
    @Bean
    @Qualifier("userSessionAuthenticationStrategy")
    SessionAuthenticationStrategy userSessionAuthenticationStrategy(
            SessionRegistry sessionRegistry,
            SessionLimitService sessionLimitService,
            SecurityAuditService securityAuditService
    ) {
        UserConcurrentSessionControlAuthenticationStrategy concurrent =
                new UserConcurrentSessionControlAuthenticationStrategy(
                        sessionRegistry,
                        sessionLimitService,
                        securityAuditService
                );
        return new CompositeSessionAuthenticationStrategy(List.of(
                concurrent,
                new ChangeSessionIdAuthenticationStrategy(),
                new RegisterSessionAuthenticationStrategy(sessionRegistry)
        ));
    }

    /**
     * 管理员：固定最多 1 个会话。
     */
    @Bean
    @Qualifier("adminSessionAuthenticationStrategy")
    SessionAuthenticationStrategy adminSessionAuthenticationStrategy(SessionRegistry sessionRegistry) {
        ConcurrentSessionControlAuthenticationStrategy concurrent =
                new ConcurrentSessionControlAuthenticationStrategy(sessionRegistry);
        concurrent.setMaximumSessions(SessionLimitService.ADMIN_MAX_SESSIONS);
        concurrent.setExceptionIfMaximumExceeded(false);
        return new CompositeSessionAuthenticationStrategy(List.of(
                concurrent,
                new ChangeSessionIdAuthenticationStrategy(),
                new RegisterSessionAuthenticationStrategy(sessionRegistry)
        ));
    }
}
