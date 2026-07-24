package com.godlei.onlinesafe.auth.web;

import java.time.Instant;

public record ProfileResponse(
        String userId,
        String username,
        String maskedPhone,
        String avatarUrl,
        boolean canChangeUsername,
        Instant usernameChangeAvailableAt
) {
}
