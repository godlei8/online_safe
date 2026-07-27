package com.godlei.onlinesafe.datarecovery.web;

public record CreateRestoreOperationRequest(
        String sourceBackupId,
        Integer formatVersion,
        int totalCount,
        boolean includeTrashAssets
) {
}
