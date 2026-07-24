package com.godlei.onlinesafe.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

public record AdminUserPrincipal(
        String adminId,
        String username,
        String passwordHash,
        boolean enabled
) implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Session 索引前缀，避免与个人用户同名时并发会话互相挤掉。 */
    public static final String SESSION_NAME_PREFIX = "admin:";

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    /**
     * 用于 Spring Session / 并发会话控制的主体名；对外展示仍用 {@link #username()}。
     */
    @Override
    public String getUsername() {
        return SESSION_NAME_PREFIX + username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
