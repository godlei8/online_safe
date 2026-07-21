package com.godlei.onlinesafe.vault.web;

import com.godlei.onlinesafe.security.AppUserPrincipal;
import com.godlei.onlinesafe.vault.application.VaultKeyBundleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vault/key-bundle")
public class VaultKeyBundleController {

    private final VaultKeyBundleService vaultKeyBundleService;

    public VaultKeyBundleController(VaultKeyBundleService vaultKeyBundleService) {
        this.vaultKeyBundleService = vaultKeyBundleService;
    }

    @GetMapping
    public VaultKeyBundleResponse get(@AuthenticationPrincipal AppUserPrincipal principal) {
        return vaultKeyBundleService.get(principal.userId());
    }

    @PutMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VaultKeyBundleResponse create(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody VaultKeyBundleRequest request
    ) {
        return vaultKeyBundleService.create(principal.userId(), request);
    }

    @PatchMapping
    public VaultKeyBundleResponse replace(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody VaultKeyBundleReplaceRequest request
    ) {
        return vaultKeyBundleService.replace(principal.userId(), request);
    }
}
