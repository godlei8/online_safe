package com.godlei.onlinesafe.admin.infrastructure;

import com.godlei.onlinesafe.admin.domain.InvitePurpose;
import com.godlei.onlinesafe.admin.domain.InviteStatus;
import com.godlei.onlinesafe.admin.domain.RegistrationInvite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RegistrationInviteRepository extends JpaRepository<RegistrationInvite, String> {

    Optional<RegistrationInvite> findByCodeHash(String codeHash);

    long countByStatus(InviteStatus status);

    @Query("""
            SELECT COUNT(i) FROM RegistrationInvite i
            WHERE i.status = :active
              AND i.expiresAt IS NOT NULL
              AND i.expiresAt > :now
              AND i.expiresAt <= :horizon
            """)
    long countExpiringSoon(
            @Param("active") InviteStatus active,
            @Param("now") Instant now,
            @Param("horizon") Instant horizon
    );

    @Query("""
            SELECT i FROM RegistrationInvite i
            WHERE (:status IS NULL OR i.status = :status)
              AND (:purpose IS NULL OR i.purpose = :purpose)
              AND (
                :singleUse IS NULL
                OR (:singleUse = TRUE AND i.maxUses = 1)
                OR (:singleUse = FALSE AND i.maxUses > 1)
              )
              AND (
                :query IS NULL OR :query = ''
                OR LOWER(COALESCE(i.note, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR i.createdByAdminId IN (
                    SELECT a.id FROM AdminUser a
                    WHERE LOWER(a.username) LIKE LOWER(CONCAT('%', :query, '%'))
                )
              )
            """)
    Page<RegistrationInvite> search(
            @Param("status") InviteStatus status,
            @Param("purpose") InvitePurpose purpose,
            @Param("singleUse") Boolean singleUse,
            @Param("query") String query,
            Pageable pageable
    );
}
