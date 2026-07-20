package com.godlei.onlinesafe.vault.web;

import com.godlei.onlinesafe.security.AppUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class UserApiProbeController {

    @GetMapping("/ping")
    public Map<String, String> ping(@AuthenticationPrincipal AppUserPrincipal principal) {
        return Map.of("status", "ok", "userId", principal.userId());
    }
}
