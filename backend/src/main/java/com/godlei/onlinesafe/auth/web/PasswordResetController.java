package com.godlei.onlinesafe.auth.web;

import com.godlei.onlinesafe.auth.application.PasswordResetService;
import com.godlei.onlinesafe.auth.domain.BuiltinSecurityQuestions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @GetMapping("/security-questions/builtins")
    public List<Map<String, String>> builtinQuestions() {
        return BuiltinSecurityQuestions.all().stream()
                .map(q -> Map.of("code", q.code(), "text", q.text()))
                .toList();
    }

    @PostMapping("/password-reset/lookup")
    public PasswordResetLookupResponse lookup(
            @Valid @RequestBody PasswordResetLookupRequest request,
            HttpServletRequest servletRequest
    ) {
        return passwordResetService.lookup(request.identifier(), clientKey(servletRequest));
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
