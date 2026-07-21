package com.godlei.onlinesafe.admin.web;

import com.godlei.onlinesafe.admin.application.InvitationService;
import com.godlei.onlinesafe.admin.domain.InviteStatus;
import com.godlei.onlinesafe.security.AdminUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/invitations")
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @GetMapping
    public Page<InvitationResponse> list(
            @RequestParam(required = false) InviteStatus status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Boolean singleUse = null;
        if ("SINGLE".equalsIgnoreCase(type)) {
            singleUse = true;
        } else if ("MULTI".equalsIgnoreCase(type)) {
            singleUse = false;
        }
        int safeSize = Math.min(Math.max(size, 1), 100);
        return invitationService.list(
                status,
                singleUse,
                q,
                PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
    }

    @GetMapping("/stats")
    public InvitationStatsResponse stats() {
        return invitationService.stats();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateInvitationResponse create(
            @AuthenticationPrincipal AdminUserPrincipal principal,
            @Valid @RequestBody CreateInvitationRequest request
    ) {
        return invitationService.create(principal.adminId(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        invitationService.delete(id);
    }

    @GetMapping("/{id}/plain-code")
    public PlainInvitationCodeResponse plainCode(@PathVariable String id) {
        return invitationService.revealPlainCode(id);
    }
}
