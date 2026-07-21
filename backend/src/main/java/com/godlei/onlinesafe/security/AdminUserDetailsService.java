package com.godlei.onlinesafe.security;

import com.godlei.onlinesafe.admin.domain.AdminUser;
import com.godlei.onlinesafe.admin.domain.AdminUserStatus;
import com.godlei.onlinesafe.admin.infrastructure.AdminUserRepository;
import com.godlei.onlinesafe.auth.application.UsernameNormalizer;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminUserRepository adminUserRepository;
    private final UsernameNormalizer usernameNormalizer;

    public AdminUserDetailsService(AdminUserRepository adminUserRepository, UsernameNormalizer usernameNormalizer) {
        this.adminUserRepository = adminUserRepository;
        this.usernameNormalizer = usernameNormalizer;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalized = usernameNormalizer.normalizeForLogin(username);
        AdminUser admin = adminUserRepository.findByNormalizedUsername(normalized)
                .orElseThrow(() -> new UsernameNotFoundException("账号或密码错误"));
        return new AdminUserPrincipal(
                admin.getId(),
                admin.getUsername(),
                admin.getPasswordHash(),
                admin.getStatus() == AdminUserStatus.ACTIVE
        );
    }
}
