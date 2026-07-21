package com.godlei.onlinesafe.admin.web;

import com.godlei.onlinesafe.security.AdminUserPrincipal;
import org.springframework.security.core.Authentication;

public record AdminSessionResponse(
        boolean authenticated,
        String adminId,
        String username,
        String role
) {
    public static AdminSessionResponse from(Authentication authentication) {
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AdminUserPrincipal principal) {
            return new AdminSessionResponse(true, principal.adminId(), principal.username(), "ADMIN");
        }
        return new AdminSessionResponse(false, null, null, null);
    }
}
