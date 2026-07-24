package com.godlei.onlinesafe.auth.web;

import com.godlei.onlinesafe.auth.infrastructure.AppUserRepository;
import com.godlei.onlinesafe.security.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
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
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final AppUserRepository appUserRepository;
    private final Clock clock;

    public AuthController(
            AuthenticationManager authenticationManager,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            SecurityContextRepository securityContextRepository,
            AppUserRepository appUserRepository,
            Clock clock
    ) {
        this.authenticationManager = authenticationManager;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.securityContextRepository = securityContextRepository;
        this.appUserRepository = appUserRepository;
        this.clock = clock;
    }

    @PostMapping("/login")
    @Transactional
    public SessionResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.identifier(), request.password())
        );

        sessionAuthenticationStrategy.onAuthentication(authentication, servletRequest, servletResponse);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);

        if (authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            return appUserRepository.findById(principal.userId()).map(user -> {
                user.recordLogin(clock.instant());
                appUserRepository.save(user);
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
