package com.godlei.onlinesafe.vault.infrastructure;

import com.godlei.onlinesafe.vault.domain.PrivateTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrivateTemplateRepository extends JpaRepository<PrivateTemplate, String> {

    List<PrivateTemplate> findByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(String ownerId);

    Optional<PrivateTemplate> findByIdAndOwnerIdAndDeletedAtIsNull(String id, String ownerId);

    void deleteByOwnerId(String ownerId);
}
