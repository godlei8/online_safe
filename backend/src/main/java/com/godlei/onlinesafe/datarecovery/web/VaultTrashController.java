package com.godlei.onlinesafe.datarecovery.web;

import com.godlei.onlinesafe.datarecovery.application.RecentReauthenticationService;
import com.godlei.onlinesafe.datarecovery.application.VaultTrashService;
import com.godlei.onlinesafe.security.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data-security/trash")
public class VaultTrashController {

    private final VaultTrashService trashService;
    private final RecentReauthenticationService reauthenticationService;

    public VaultTrashController(
            VaultTrashService trashService,
            RecentReauthenticationService reauthenticationService
    ) {
        this.trashService = trashService;
        this.reauthenticationService = reauthenticationService;
    }

    @GetMapping
    public TrashPageResponse list(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "ALL") TrashListType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return trashService.list(principal.userId(), keyword, type, page, size);
    }

    @PostMapping("/{type}/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable TrashAssetType type,
            @PathVariable String id
    ) {
        trashService.restore(principal.userId(), principal.username(), type, id);
    }

    @DeleteMapping("/{type}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void purgeOne(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable TrashAssetType type,
            @PathVariable String id,
            HttpServletRequest request
    ) {
        reauthenticationService.requireRecent(request);
        trashService.purgeOne(principal.userId(), principal.username(), type, id);
    }

    @PostMapping("/purge")
    public TrashPurgeResponse purgeAll(
            @AuthenticationPrincipal AppUserPrincipal principal,
            HttpServletRequest request
    ) {
        reauthenticationService.requireRecent(request);
        return trashService.purgeAll(principal.userId(), principal.username());
    }
}
