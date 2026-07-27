package com.godlei.onlinesafe.datarecovery.web;

import com.godlei.onlinesafe.datarecovery.application.RecentReauthenticationService;
import com.godlei.onlinesafe.datarecovery.application.VaultRestoreService;
import com.godlei.onlinesafe.security.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data-security")
public class VaultRestoreController {

    private final VaultRestoreService restoreService;
    private final RecentReauthenticationService reauthenticationService;

    public VaultRestoreController(
            VaultRestoreService restoreService,
            RecentReauthenticationService reauthenticationService
    ) {
        this.restoreService = restoreService;
        this.reauthenticationService = reauthenticationService;
    }

    @PostMapping("/restores/preflight")
    public RestorePreflightResponse preflight(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestBody RestorePreflightRequest request
    ) {
        return restoreService.preflight(principal.userId(), request);
    }

    @PostMapping("/restore-operations")
    public RestoreOperationResponse create(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestBody CreateRestoreOperationRequest request,
            HttpServletRequest servletRequest
    ) {
        reauthenticationService.requireRecent(servletRequest);
        return restoreService.createOperation(principal.userId(), principal.username(), request);
    }

    @PutMapping("/restore-operations/{operationId}/batches/{batchNo}")
    public RestoreBatchResponse submitBatch(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable String operationId,
            @PathVariable int batchNo,
            @RequestBody RestoreBatchRequest request
    ) {
        return restoreService.submitBatch(principal.userId(), operationId, batchNo, request);
    }

    @PostMapping("/restore-operations/{operationId}/complete")
    public RestoreOperationResponse complete(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable String operationId
    ) {
        return restoreService.complete(principal.userId(), principal.username(), operationId);
    }

    @GetMapping("/restore-operations/{operationId}")
    public RestoreOperationResponse get(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable String operationId
    ) {
        return restoreService.get(principal.userId(), operationId);
    }
}
