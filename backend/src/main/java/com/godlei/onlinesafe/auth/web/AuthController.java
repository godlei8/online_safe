package com.godlei.onlinesafe.auth.web;

import com.godlei.onlinesafe.audit.application.SecurityAuditService;
import com.godlei.onlinesafe.auth.infrastructure.AppUserRepository;
import com.godlei.onlinesafe.security.AppUserPrincipal;
import com.godlei.onlinesafe.session.application.SessionMetadataService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy userSessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final AppUserRepository appUserRepository;
    private final SecurityAuditService securityAuditService;
    private final SessionMetadataService sessionMetadataService;
    private final Clock clock;

    public AuthController(
            AuthenticationManager authenticationManager,
            @Qualifier("userSessionAuthenticationStrategy") SessionAuthenticationStrategy userSessionAuthenticationStrategy,
            SecurityContextRepository securityContextRepository,
            AppUserRepository appUserRepository,
            SecurityAuditService securityAuditService,
            SessionMetadataService sessionMetadataService,
            Clock clock
    ) {
        this.authenticationManager = authenticationManager;
        this.userSessionAuthenticationStrategy = userSessionAuthenticationStrategy;
        this.securityContextRepository = securityContextRepository;
        this.appUserRepository = appUserRepository;
        this.securityAuditService = securityAuditService;
        this.sessionMetadataService = sessionMetadataService;
        this.clock = clock;
    }

    @PostMapping("/login")
    @Transactional
    public SessionResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.identifier(), request.password())
            );
        } catch (AuthenticationException exception) {
            securityAuditService.recordAuthFailureUser(request.identifier(), servletRequest);
            throw exception;
        }

        userSessionAuthenticationStrategy.onAuthentication(authentication, servletRequest, servletResponse);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);

        HttpSession session = servletRequest.getSession(false);
        sessionMetadataService.writeOnLogin(servletRequest, session);

        if (authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            return appUserRepository.findById(principal.userId()).map(user -> {
                user.recordLogin(clock.instant());
                appUserRepository.save(user);
                securityAuditService.recordAuthSuccessUser(user.getId(), user.getUsername(), servletRequest);
                return SessionResponse.authenticated(user.getId(), user.getUsername(), user.getAvatarUrl());
            }).orElseGet(() -> SessionResponse.from(authentication));
        }

        return SessionResponse.from(authentication);
    }

    @GetMapping("/session")
    public SessionResponse currentSession(Authentication authentication) {
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            return appUserRepository.findById(principal.userId())
                    .map(user -> SessionResponse.authenticated(user.getId(), user.getUsername(), user.getAvatarUrl()))
                    .orElseGet(SessionResponse::anonymous);
        }
        return SessionResponse.anonymous();
    }
}
