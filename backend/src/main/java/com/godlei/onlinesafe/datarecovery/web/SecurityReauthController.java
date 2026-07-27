package com.godlei.onlinesafe.datarecovery.web;

import com.godlei.onlinesafe.datarecovery.application.RecentReauthenticationService;
import com.godlei.onlinesafe.security.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/security")
public class SecurityReauthController {

    private final RecentReauthenticationService reauthenticationService;

    public SecurityReauthController(RecentReauthenticationService reauthenticationService) {
        this.reauthenticationService = reauthenticationService;
    }

    @PostMapping("/reauth")
    public ReauthResponse reauth(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody ReauthRequest request,
            HttpServletRequest servletRequest
    ) {
        return new ReauthResponse(reauthenticationService.reauthenticate(principal, request.password(), servletRequest));
    }
}
