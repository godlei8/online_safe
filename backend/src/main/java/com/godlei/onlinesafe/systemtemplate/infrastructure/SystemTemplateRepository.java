package com.godlei.onlinesafe.systemtemplate.infrastructure;

import com.godlei.onlinesafe.systemtemplate.domain.SystemTemplate;
import com.godlei.onlinesafe.systemtemplate.domain.SystemTemplateStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SystemTemplateRepository extends JpaRepository<SystemTemplate, String> {

    Page<SystemTemplate> findByStatus(SystemTemplateStatus status, Pageable pageable);

    @Query("""
            SELECT t FROM SystemTemplate t
            WHERE (:status IS NULL OR t.status = :status)
              AND (:platform IS NULL OR LOWER(t.platform) LIKE LOWER(CONCAT('%', :platform, '%')))
              AND (:q IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<SystemTemplate> searchAdmin(
            @Param("status") SystemTemplateStatus status,
            @Param("platform") String platform,
            @Param("q") String q,
            Pageable pageable
    );

    @Query("""
            SELECT t FROM SystemTemplate t
            WHERE t.status = com.godlei.onlinesafe.systemtemplate.domain.SystemTemplateStatus.PUBLISHED
              AND (:platform IS NULL OR LOWER(t.platform) LIKE LOWER(CONCAT('%', :platform, '%')))
              AND (:q IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY t.sortOrder ASC, t.updatedAt DESC
            """)
    List<SystemTemplate> findPublished(
            @Param("platform") String platform,
            @Param("q") String q
    );

    Optional<SystemTemplate> findByIdAndStatus(String id, SystemTemplateStatus status);
}
