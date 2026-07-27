package com.godlei.onlinesafe.vault.infrastructure;

import com.godlei.onlinesafe.vault.domain.PrivateTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PrivateTemplateRepository extends JpaRepository<PrivateTemplate, String> {

    List<PrivateTemplate> findByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(String ownerId);

    Optional<PrivateTemplate> findByIdAndOwnerIdAndDeletedAtIsNull(String id, String ownerId);

    Optional<PrivateTemplate> findByIdAndOwnerIdAndDeletedAtIsNotNull(String id, String ownerId);

    Optional<PrivateTemplate> findByIdAndOwnerId(String id, String ownerId);

    long countByOwnerIdAndDeletedAtIsNotNull(String ownerId);

    Optional<PrivateTemplate> findFirstByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtAsc(String ownerId);

    Page<PrivateTemplate> findByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(String ownerId, Pageable pageable);

    @Query("""
            select t from PrivateTemplate t
            where t.deletedAt is not null and t.deletedAt < :cutoff
            order by t.deletedAt asc
            """)
    List<PrivateTemplate> findExpiredTrash(@Param("cutoff") Instant cutoff, Pageable pageable);

    @Modifying
    @Query("delete from PrivateTemplate t where t.id in :ids")
    int deleteByIdIn(@Param("ids") List<String> ids);

    void deleteByOwnerId(String ownerId);

    List<PrivateTemplate> findByOwnerIdOrderByUpdatedAtDesc(String ownerId);
}
