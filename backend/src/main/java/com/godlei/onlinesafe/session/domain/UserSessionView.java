package com.godlei.onlinesafe.session.domain;

import java.time.Instant;

public record UserSessionView(
        String publicId,
        String internalSessionId,
        boolean current,
        String deviceType,
        String browserFamily,
        String osFamily,
        String displayName,
        String ipMasked,
        Instant loginAt,
        Instant lastActiveAt,
        Instant expiresAt
) {
}
