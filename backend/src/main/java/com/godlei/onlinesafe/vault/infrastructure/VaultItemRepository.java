package com.godlei.onlinesafe.vault.infrastructure;

import com.godlei.onlinesafe.vault.domain.VaultItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VaultItemRepository extends JpaRepository<VaultItem, String> {

    List<VaultItem> findByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(String ownerId);

    Optional<VaultItem> findByIdAndOwnerIdAndDeletedAtIsNull(String id, String ownerId);

    void deleteByOwnerId(String ownerId);
}
