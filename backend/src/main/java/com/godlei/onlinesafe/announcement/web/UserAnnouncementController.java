package com.godlei.onlinesafe.announcement.web;

import com.godlei.onlinesafe.announcement.application.AnnouncementService;
import com.godlei.onlinesafe.security.AppUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/announcements")
public class UserAnnouncementController {

    private final AnnouncementService announcementService;

    public UserAnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping("/inbox")
    public AnnouncementInboxResponse inbox(@AuthenticationPrincipal AppUserPrincipal principal) {
        return announcementService.inbox(principal.userId());
    }

    @GetMapping
    public List<AnnouncementUserResponse> list(@AuthenticationPrincipal AppUserPrincipal principal) {
        return announcementService.listForUser(principal.userId());
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable String id
    ) {
        announcementService.markRead(principal.userId(), id);
    }
}
