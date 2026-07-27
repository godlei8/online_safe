package com.godlei.onlinesafe.vault.infrastructure;

import com.godlei.onlinesafe.vault.domain.VaultItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VaultItemRepository extends JpaRepository<VaultItem, String> {

    List<VaultItem> findByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(String ownerId);

    Optional<VaultItem> findByIdAndOwnerIdAndDeletedAtIsNull(String id, String ownerId);

    Optional<VaultItem> findByIdAndOwnerIdAndDeletedAtIsNotNull(String id, String ownerId);

    Optional<VaultItem> findByIdAndOwnerId(String id, String ownerId);

    long countByOwnerIdAndDeletedAtIsNotNull(String ownerId);

    Optional<VaultItem> findFirstByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtAsc(String ownerId);

    Page<VaultItem> findByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(String ownerId, Pageable pageable);

    @Query("""
            select i from VaultItem i
            where i.deletedAt is not null and i.deletedAt < :cutoff
            order by i.deletedAt asc
            """)
    List<VaultItem> findExpiredTrash(@Param("cutoff") Instant cutoff, Pageable pageable);

    @Modifying
    @Query("delete from VaultItem i where i.id in :ids")
    int deleteByIdIn(@Param("ids") List<String> ids);

    void deleteByOwnerId(String ownerId);

    List<VaultItem> findByOwnerIdOrderByUpdatedAtDesc(String ownerId);
}
