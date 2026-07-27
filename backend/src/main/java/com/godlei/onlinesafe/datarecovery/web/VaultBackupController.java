package com.godlei.onlinesafe.datarecovery.web;

import com.godlei.onlinesafe.datarecovery.application.RecentReauthenticationService;
import com.godlei.onlinesafe.datarecovery.application.VaultBackupSnapshotService;
import com.godlei.onlinesafe.security.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data-security")
public class VaultBackupController {

    private final VaultBackupSnapshotService snapshotService;
    private final RecentReauthenticationService reauthenticationService;

    public VaultBackupController(
            VaultBackupSnapshotService snapshotService,
            RecentReauthenticationService reauthenticationService
    ) {
        this.snapshotService = snapshotService;
        this.reauthenticationService = reauthenticationService;
    }

    @PostMapping("/backup-snapshots")
    public ResponseEntity<BackupSnapshotResponse> createSnapshot(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestBody(required = false) BackupSnapshotRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        reauthenticationService.requireRecent(servletRequest);
        BackupSnapshotResponse body = snapshotService.createSnapshot(
                principal.userId(),
                principal.username(),
                request == null ? new BackupSnapshotRequest(true) : request
        );
        servletResponse.setHeader("Pragma", "no-cache");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .body(body);
    }
}
