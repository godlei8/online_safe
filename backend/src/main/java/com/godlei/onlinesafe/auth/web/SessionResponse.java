package com.godlei.onlinesafe.auth.web;

import com.godlei.onlinesafe.security.AppUserPrincipal;
import org.springframework.security.core.Authentication;

public record SessionResponse(
        boolean authenticated,
        String userId,
        String username,
        String role
) {
    public static SessionResponse from(Authentication authentication) {
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            return new SessionResponse(true, principal.userId(), principal.username(), "USER");
        }
        return new SessionResponse(false, null, null, null);
    }
}
