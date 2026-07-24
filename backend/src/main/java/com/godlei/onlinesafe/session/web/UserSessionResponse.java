package com.godlei.onlinesafe.session.web;

import com.godlei.onlinesafe.session.domain.UserSessionView;

import java.time.Instant;

public record UserSessionResponse(
        String id,
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
    public static UserSessionResponse from(UserSessionView view) {
        return new UserSessionResponse(
                view.publicId(),
                view.current(),
                view.deviceType(),
                view.browserFamily(),
                view.osFamily(),
                view.displayName(),
                view.ipMasked(),
                view.loginAt(),
                view.lastActiveAt(),
                view.expiresAt()
        );
    }
}
