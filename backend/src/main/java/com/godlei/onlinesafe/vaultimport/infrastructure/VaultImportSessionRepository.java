package com.godlei.onlinesafe.vaultimport.infrastructure;

import com.godlei.onlinesafe.vaultimport.domain.VaultImportSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VaultImportSessionRepository extends JpaRepository<VaultImportSession, String> {

    Optional<VaultImportSession> findByIdAndOwnerId(String id, String ownerId);

    List<VaultImportSession> findByExpiresAtBefore(Instant cutoff);

    @Modifying
    @Query("""
            delete from VaultImportSession s
            where s.expiresAt < :cutoff
               or (s.status in ('COMMITTED', 'DISCARDED', 'FAILED') and s.updatedAt < :staleBefore)
            """)
    int deleteExpiredOrStale(@Param("cutoff") Instant cutoff, @Param("staleBefore") Instant staleBefore);
}
