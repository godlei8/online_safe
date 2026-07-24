package com.godlei.onlinesafe.settings.web;

import com.godlei.onlinesafe.security.AdminUserPrincipal;
import com.godlei.onlinesafe.settings.application.SystemSettingService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/system-settings")
public class SystemSettingAdminController {

    private final SystemSettingService systemSettingService;

    public SystemSettingAdminController(SystemSettingService systemSettingService) {
        this.systemSettingService = systemSettingService;
    }

    @GetMapping
    public SystemSettingsResponse get() {
        return systemSettingService.getAll();
    }

    @PostMapping("/validate")
    public SystemSettingValidateResponse validate(@Valid @RequestBody SystemSettingUpdateRequest request) {
        return systemSettingService.validate(request);
    }

    @PutMapping
    public SystemSettingsResponse update(
            @AuthenticationPrincipal AdminUserPrincipal principal,
            @Valid @RequestBody SystemSettingUpdateRequest request
    ) {
        return systemSettingService.update(principal.adminId(), request);
    }
}
