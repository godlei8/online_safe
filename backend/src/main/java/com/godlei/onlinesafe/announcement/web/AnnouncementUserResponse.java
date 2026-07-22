package com.godlei.onlinesafe.announcement.web;

import java.time.Instant;

public record AnnouncementUserResponse(
        String id,
        String title,
        String body,
        boolean pinned,
        Instant startsAt,
        Instant endsAt,
        Instant publishedAt,
        boolean read
) {
}
