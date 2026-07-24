package com.godlei.onlinesafe.session.web;

import java.util.List;

public record UserSessionListResponse(
        int activeCount,
        int maxActiveSessions,
        List<UserSessionResponse> sessions
) {
}
