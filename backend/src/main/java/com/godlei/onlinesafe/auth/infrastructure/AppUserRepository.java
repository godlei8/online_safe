package com.godlei.onlinesafe.auth.infrastructure;

import com.godlei.onlinesafe.auth.domain.AppUser;
import com.godlei.onlinesafe.auth.domain.AppUserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, String>, JpaSpecificationExecutor<AppUser> {

    boolean existsByPhone(String phone);

    boolean existsByNormalizedUsername(String normalizedUsername);

    boolean existsByNormalizedUsernameAndIdNot(String normalizedUsername, String id);

    Optional<AppUser> findByPhone(String phone);

    Optional<AppUser> findByNormalizedUsername(String normalizedUsername);

    long countByStatus(AppUserStatus status);

    @Query("select count(u) from AppUser u where u.lastLoginAt is not null and u.lastLoginAt >= :since")
    long countActiveSince(@Param("since") Instant since);
}
