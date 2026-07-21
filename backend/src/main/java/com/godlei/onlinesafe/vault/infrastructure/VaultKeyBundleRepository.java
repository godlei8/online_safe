package com.godlei.onlinesafe.vault.infrastructure;

import com.godlei.onlinesafe.vault.domain.VaultKeyBundle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VaultKeyBundleRepository extends JpaRepository<VaultKeyBundle, String> {

    Optional<VaultKeyBundle> findByOwnerId(String ownerId);

    boolean existsByOwnerId(String ownerId);

    void deleteByOwnerId(String ownerId);
}
