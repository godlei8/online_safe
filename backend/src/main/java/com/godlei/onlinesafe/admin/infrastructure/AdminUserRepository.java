package com.godlei.onlinesafe.admin.infrastructure;

import com.godlei.onlinesafe.admin.domain.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUser, String> {
    Optional<AdminUser> findByNormalizedUsername(String normalizedUsername);
}
