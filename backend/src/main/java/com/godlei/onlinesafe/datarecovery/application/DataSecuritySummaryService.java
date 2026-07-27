package com.godlei.onlinesafe.datarecovery.application;

import com.godlei.onlinesafe.datarecovery.web.DataSecuritySummaryResponse;
import com.godlei.onlinesafe.settings.application.SystemSettingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class DataSecuritySummaryService {

    private final VaultTrashService trashService;
    private final SystemSettingService systemSettingService;
    private final VaultIntegrityScanService integrityScanService;
    private final VaultBackupSnapshotService backupSnapshotService;

    public DataSecuritySummaryService(
            VaultTrashService trashService,
            SystemSettingService systemSettingService,
            VaultIntegrityScanService integrityScanService,
            VaultBackupSnapshotService backupSnapshotService
    ) {
        this.trashService = trashService;
        this.systemSettingService = systemSettingService;
        this.integrityScanService = integrityScanService;
        this.backupSnapshotService = backupSnapshotService;
    }

    @Transactional(readOnly = true)
    public DataSecuritySummaryResponse summary(String ownerId) {
        int retentionDays = systemSettingService.recycleBinRetentionDaysSafe();
        long itemCount = trashService.itemCount(ownerId);
        long templateCount = trashService.templateCount(ownerId);
        Instant nearestPurgeAt = trashService.nearestPurgeAt(ownerId, retentionDays);
        var encryption = integrityScanService.userStatus(ownerId);
        Instant lastSnapshotAt = backupSnapshotService.lastSnapshotAt(ownerId);
        return new DataSecuritySummaryResponse(
                new DataSecuritySummaryResponse.EncryptionSummary(encryption.status(), encryption.lastCheckedAt()),
                new DataSecuritySummaryResponse.BackupSummary(lastSnapshotAt),
                new DataSecuritySummaryResponse.TrashSummary(itemCount, templateCount, nearestPurgeAt, retentionDays)
        );
    }
}
