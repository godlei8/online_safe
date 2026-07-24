package com.godlei.onlinesafe.security;

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
     * 登录时：同账号仅保留 1 个有效会话（多地登录挤掉旧会话）→ 换 SessionId → 登记新会话。
     */
    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(SessionRegistry sessionRegistry) {
        ConcurrentSessionControlAuthenticationStrategy concurrent =
                new ConcurrentSessionControlAuthenticationStrategy(sessionRegistry);
        concurrent.setMaximumSessions(1);
        concurrent.setExceptionIfMaximumExceeded(false);
        return new CompositeSessionAuthenticationStrategy(List.of(
                concurrent,
                new ChangeSessionIdAuthenticationStrategy(),
                new RegisterSessionAuthenticationStrategy(sessionRegistry)
        ));
    }
}
