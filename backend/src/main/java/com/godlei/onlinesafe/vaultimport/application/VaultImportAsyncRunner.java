package com.godlei.onlinesafe.vaultimport.application;

import com.godlei.onlinesafe.vaultimport.domain.ImportMode;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class VaultImportAsyncRunner {

    private final VaultImportService vaultImportService;

    public VaultImportAsyncRunner(@Lazy VaultImportService vaultImportService) {
        this.vaultImportService = vaultImportService;
    }

    @Async("importTaskExecutor")
    public void process(String sessionId, String ownerId, byte[] bytes, String fileName, ImportMode mode) {
        vaultImportService.processUploadedFile(sessionId, ownerId, bytes, fileName, mode);
    }
}
