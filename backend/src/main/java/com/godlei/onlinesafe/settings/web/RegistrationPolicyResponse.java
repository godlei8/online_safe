package com.godlei.onlinesafe.settings.web;

public record RegistrationPolicyResponse(
        boolean registrationEnabled,
        String mode,
        boolean smsRequired,
        boolean inviteRequired,
        int passwordMinLength
) {
}
