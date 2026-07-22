package com.godlei.onlinesafe.vault.web;

import com.godlei.onlinesafe.security.AppUserPrincipal;
import com.godlei.onlinesafe.vault.application.VaultItemService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vault/items")
public class VaultItemController {

    private final VaultItemService vaultItemService;

    public VaultItemController(VaultItemService vaultItemService) {
        this.vaultItemService = vaultItemService;
    }

    @GetMapping
    public List<VaultRecordResponse> list(@AuthenticationPrincipal AppUserPrincipal principal) {
        return vaultItemService.list(principal.userId());
    }

    @GetMapping("/{id}")
    public VaultRecordResponse get(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable String id
    ) {
        return vaultItemService.get(principal.userId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VaultRecordResponse create(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody VaultRecordRequest request
    ) {
        return vaultItemService.create(principal.userId(), request);
    }

    @PutMapping("/{id}")
    public VaultRecordResponse update(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable String id,
            @Valid @RequestBody VaultRecordRequest request
    ) {
        return vaultItemService.update(principal.userId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable String id
    ) {
        vaultItemService.delete(principal.userId(), id);
    }
}
