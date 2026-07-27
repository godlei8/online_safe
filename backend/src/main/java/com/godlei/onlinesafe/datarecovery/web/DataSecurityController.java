package com.godlei.onlinesafe.datarecovery.web;

import com.godlei.onlinesafe.datarecovery.application.DataSecuritySummaryService;
import com.godlei.onlinesafe.datarecovery.application.VaultIntegrityScanService;
import com.godlei.onlinesafe.security.AppUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data-security")
public class DataSecurityController {

    private final DataSecuritySummaryService summaryService;
    private final VaultIntegrityScanService integrityScanService;

    public DataSecurityController(
            DataSecuritySummaryService summaryService,
            VaultIntegrityScanService integrityScanService
    ) {
        this.summaryService = summaryService;
        this.integrityScanService = integrityScanService;
    }

    @GetMapping("/summary")
    public DataSecuritySummaryResponse summary(@AuthenticationPrincipal AppUserPrincipal principal) {
        return summaryService.summary(principal.userId());
    }

    @PostMapping("/integrity-scan")
    public VaultIntegrityScanService.UserScanStatus integrityScan(@AuthenticationPrincipal AppUserPrincipal principal) {
        return integrityScanService.scanForUser(principal.userId(), principal.username());
    }
}
