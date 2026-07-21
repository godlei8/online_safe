package com.godlei.onlinesafe.admin.infrastructure;

import com.godlei.onlinesafe.admin.domain.RegistrationInviteRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationInviteRedemptionRepository extends JpaRepository<RegistrationInviteRedemption, String> {
    void deleteByInviteId(String inviteId);
}
