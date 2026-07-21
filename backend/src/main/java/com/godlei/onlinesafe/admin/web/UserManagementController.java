package com.godlei.onlinesafe.admin.web;

import com.godlei.onlinesafe.admin.application.UserManagementService;
import com.godlei.onlinesafe.auth.domain.AppUserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/users")
public class UserManagementController {

    private final UserManagementService userManagementService;

    public UserManagementController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping
    public Page<ManagedUserResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) AppUserStatus status,
            @RequestParam(required = false) String registeredWithin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        return userManagementService.list(
                q,
                status,
                registeredWithin,
                PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
    }

    @GetMapping("/stats")
    public ManagedUserStatsResponse stats() {
        return userManagementService.stats();
    }

    @PostMapping("/{id}/disable")
    public ManagedUserResponse disable(@PathVariable String id) {
        return userManagementService.disable(id);
    }

    @PostMapping("/{id}/enable")
    public ManagedUserResponse enable(@PathVariable String id) {
        return userManagementService.enable(id);
    }

    @PostMapping("/{id}/revoke-sessions")
    @ResponseStatus(HttpStatus.OK)
    public ManagedUserResponse revokeSessions(@PathVariable String id) {
        return userManagementService.revokeSessions(id);
    }
}
