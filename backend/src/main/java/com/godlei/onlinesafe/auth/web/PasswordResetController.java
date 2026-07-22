package com.godlei.onlinesafe.auth.web;

import com.godlei.onlinesafe.auth.application.PasswordResetService;
import com.godlei.onlinesafe.auth.application.SmsVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;
    private final SmsVerificationService smsVerificationService;

    public PasswordResetController(
            PasswordResetService passwordResetService,
            SmsVerificationService smsVerificationService
    ) {
        this.passwordResetService = passwordResetService;
        this.smsVerificationService = smsVerificationService;
    }

    @PostMapping("/sms/send")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendSms(@Valid @RequestBody SmsSendRequest request, HttpServletRequest servletRequest) {
        smsVerificationService.send(request.phone(), request.purpose(), clientKey(servletRequest));
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(
            @Valid @RequestBody PasswordResetConfirmRequest request,
            HttpServletRequest servletRequest
    ) {
        passwordResetService.confirm(request, clientKey(servletRequest));
    }

    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
