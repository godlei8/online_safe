package com.godlei.onlinesafe.vaultimport.web;

import com.godlei.onlinesafe.security.AppUserPrincipal;
import com.godlei.onlinesafe.vaultimport.application.VaultImportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.godlei.onlinesafe.vaultimport.domain.ImportMode;

@RestController
@RequestMapping("/api/v1/vault/import")
public class VaultImportController {

    private final VaultImportService vaultImportService;

    public VaultImportController(VaultImportService vaultImportService) {
        this.vaultImportService = vaultImportService;
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportSessionResponse create(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "mode", defaultValue = "FAST") String mode
    ) {
        return vaultImportService.createSession(principal.userId(), file, ImportMode.from(mode));
    }

    @GetMapping("/sessions/{id}")
    public ImportSessionResponse get(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable("id") String id
    ) {
        return vaultImportService.getSession(principal.userId(), id);
    }

    @PatchMapping("/sessions/{id}/candidates/{candidateId}")
    public ImportSessionResponse patchCandidate(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable("id") String id,
            @PathVariable("candidateId") String candidateId,
            @RequestBody ImportCandidatePatchRequest request
    ) {
        return vaultImportService.patchCandidate(principal.userId(), id, candidateId, request);
    }

    @PostMapping("/sessions/{id}/commit")
    public ImportCommitResponse commit(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable("id") String id,
            @RequestBody(required = false) ImportCommitRequest request,
            HttpServletRequest servletRequest
    ) {
        return vaultImportService.commit(principal.userId(), id, request, servletRequest);
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discard(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable("id") String id
    ) {
        vaultImportService.discard(principal.userId(), id);
    }
}
