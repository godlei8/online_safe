package com.godlei.onlinesafe.auth.infrastructure;

import com.godlei.onlinesafe.auth.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, String> {

    boolean existsByPhone(String phone);

    boolean existsByNormalizedUsername(String normalizedUsername);

    Optional<AppUser> findByPhone(String phone);

    Optional<AppUser> findByNormalizedUsername(String normalizedUsername);
}
