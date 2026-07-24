package com.godlei.onlinesafe.settings.web;

import com.godlei.onlinesafe.settings.application.SystemSettingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class RegistrationPolicyController {

    private final SystemSettingService systemSettingService;

    public RegistrationPolicyController(SystemSettingService systemSettingService) {
        this.systemSettingService = systemSettingService;
    }

    @GetMapping("/registration-policy")
    public RegistrationPolicyResponse policy() {
        return systemSettingService.registrationPolicy();
    }
}
