package com.godlei.onlinesafe.announcement.web;

import com.godlei.onlinesafe.announcement.application.AnnouncementService;
import com.godlei.onlinesafe.announcement.domain.AnnouncementStatus;
import com.godlei.onlinesafe.security.AdminUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/announcements")
public class AdminAnnouncementController {

    private final AnnouncementService announcementService;

    public AdminAnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping
    public Page<AnnouncementAdminResponse> list(
            @RequestParam(required = false) AnnouncementStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        return announcementService.listAdmin(
                status,
                PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "updatedAt"))
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AnnouncementAdminResponse create(
            @AuthenticationPrincipal AdminUserPrincipal principal,
            @Valid @RequestBody AnnouncementUpsertRequest request
    ) {
        return announcementService.create(principal.adminId(), request);
    }

    @PutMapping("/{id}")
    public AnnouncementAdminResponse update(
            @PathVariable String id,
            @Valid @RequestBody AnnouncementUpsertRequest request
    ) {
        return announcementService.update(id, request);
    }

    @PostMapping("/{id}/publish")
    public AnnouncementAdminResponse publish(@PathVariable String id) {
        return announcementService.publish(id);
    }

    @PostMapping("/{id}/offline")
    public AnnouncementAdminResponse offline(@PathVariable String id) {
        return announcementService.offline(id);
    }
}
