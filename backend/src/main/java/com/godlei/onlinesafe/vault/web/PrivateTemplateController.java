package com.godlei.onlinesafe.vault.web;

import com.godlei.onlinesafe.security.AppUserPrincipal;
import com.godlei.onlinesafe.vault.application.PrivateTemplateService;
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
@RequestMapping("/api/v1/vault/private-templates")
public class PrivateTemplateController {

    private final PrivateTemplateService privateTemplateService;

    public PrivateTemplateController(PrivateTemplateService privateTemplateService) {
        this.privateTemplateService = privateTemplateService;
    }

    @GetMapping
    public List<VaultRecordResponse> list(@AuthenticationPrincipal AppUserPrincipal principal) {
        return privateTemplateService.list(principal.userId());
    }

    @GetMapping("/{id}")
    public VaultRecordResponse get(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable String id
    ) {
        return privateTemplateService.get(principal.userId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VaultRecordResponse create(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody VaultRecordRequest request
    ) {
        return privateTemplateService.create(principal.userId(), request);
    }

    @PutMapping("/{id}")
    public VaultRecordResponse update(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable String id,
            @Valid @RequestBody VaultRecordRequest request
    ) {
        return privateTemplateService.update(principal.userId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable String id
    ) {
        privateTemplateService.delete(principal.userId(), id);
    }
}
