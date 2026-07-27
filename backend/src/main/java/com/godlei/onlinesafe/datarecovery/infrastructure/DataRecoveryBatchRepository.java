package com.godlei.onlinesafe.datarecovery.infrastructure;

import com.godlei.onlinesafe.datarecovery.domain.DataRecoveryBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DataRecoveryBatchRepository extends JpaRepository<DataRecoveryBatch, DataRecoveryBatch.Pk> {

    Optional<DataRecoveryBatch> findByOperationIdAndBatchNo(String operationId, int batchNo);
}
