package com.godlei.onlinesafe.systemtemplate.web;

import com.godlei.onlinesafe.security.AdminUserPrincipal;
import com.godlei.onlinesafe.systemtemplate.application.SystemTemplateService;
import com.godlei.onlinesafe.systemtemplate.domain.SystemTemplateStatus;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/system-templates")
public class SystemTemplateAdminController {

    private final SystemTemplateService systemTemplateService;

    public SystemTemplateAdminController(SystemTemplateService systemTemplateService) {
        this.systemTemplateService = systemTemplateService;
    }

    @GetMapping
    public Page<SystemTemplateAdminResponse> list(
            @RequestParam(required = false) SystemTemplateStatus status,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        return systemTemplateService.listAdmin(
                status,
                platform,
                q,
                PageRequest.of(page, safeSize, Sort.by(Sort.Direction.ASC, "sortOrder")
                        .and(Sort.by(Sort.Direction.DESC, "updatedAt")))
        );
    }

    @GetMapping("/{id}")
    public SystemTemplateAdminResponse get(@PathVariable String id) {
        return systemTemplateService.getAdmin(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SystemTemplateAdminResponse create(
            @AuthenticationPrincipal AdminUserPrincipal principal,
            @Valid @RequestBody SystemTemplateUpsertRequest request
    ) {
        return systemTemplateService.create(principal.adminId(), request);
    }

    @PutMapping("/{id}")
    public SystemTemplateAdminResponse update(
            @PathVariable String id,
            @AuthenticationPrincipal AdminUserPrincipal principal,
            @Valid @RequestBody SystemTemplateUpsertRequest request
    ) {
        return systemTemplateService.update(id, principal.adminId(), request);
    }

    @PostMapping("/{id}/publish")
    public SystemTemplateAdminResponse publish(
            @PathVariable String id,
            @AuthenticationPrincipal AdminUserPrincipal principal
    ) {
        return systemTemplateService.publish(id, principal.adminId());
    }

    @PostMapping("/{id}/offline")
    public SystemTemplateAdminResponse offline(
            @PathVariable String id,
            @AuthenticationPrincipal AdminUserPrincipal principal
    ) {
        return systemTemplateService.offline(id, principal.adminId());
    }

    @PatchMapping("/{id}/sort")
    public SystemTemplateAdminResponse sort(
            @PathVariable String id,
            @AuthenticationPrincipal AdminUserPrincipal principal,
            @Valid @RequestBody SystemTemplateSortRequest request
    ) {
        return systemTemplateService.updateSort(id, principal.adminId(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        systemTemplateService.deleteDraft(id);
    }
}
