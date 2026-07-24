package com.godlei.onlinesafe.auth.web;

import com.godlei.onlinesafe.security.AppUserPrincipal;
import org.springframework.security.core.Authentication;

public record SessionResponse(
        boolean authenticated,
        String userId,
        String username,
        String role,
        String avatarUrl
) {
    public static SessionResponse anonymous() {
        return new SessionResponse(false, null, null, null, null);
    }

    public static SessionResponse authenticated(String userId, String username, String avatarUrl) {
        return new SessionResponse(true, userId, username, "USER", avatarUrl);
    }

    public static SessionResponse from(Authentication authentication) {
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            return authenticated(principal.userId(), principal.username(), null);
        }
        return anonymous();
    }
}
