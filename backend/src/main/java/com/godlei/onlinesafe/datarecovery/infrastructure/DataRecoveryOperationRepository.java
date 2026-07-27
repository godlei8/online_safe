package com.godlei.onlinesafe.datarecovery.infrastructure;

import com.godlei.onlinesafe.datarecovery.domain.DataRecoveryOperation;
import com.godlei.onlinesafe.datarecovery.domain.RecoveryOperationStatus;
import com.godlei.onlinesafe.datarecovery.domain.RecoveryOperationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DataRecoveryOperationRepository extends JpaRepository<DataRecoveryOperation, String> {

    Optional<DataRecoveryOperation> findByIdAndOwnerId(String id, String ownerId);

    Optional<DataRecoveryOperation> findFirstByOwnerIdAndOperationTypeOrderByCreatedAtDesc(
            String ownerId,
            RecoveryOperationType operationType
    );

    boolean existsByOwnerIdAndStatus(String ownerId, RecoveryOperationStatus status);
}
