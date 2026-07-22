package com.godlei.onlinesafe.announcement.web;

import com.godlei.onlinesafe.announcement.domain.Announcement;
import com.godlei.onlinesafe.announcement.domain.AnnouncementStatus;

import java.time.Instant;

public record AnnouncementAdminResponse(
        String id,
        String title,
        String body,
        AnnouncementStatus status,
        boolean pinned,
        Instant startsAt,
        Instant endsAt,
        Instant publishedAt,
        String createdByAdminId,
        Instant createdAt,
        Instant updatedAt
) {
    public static AnnouncementAdminResponse from(Announcement announcement) {
        return new AnnouncementAdminResponse(
                announcement.getId(),
                announcement.getTitle(),
                announcement.getBody(),
                announcement.getStatus(),
                announcement.isPinned(),
                announcement.getStartsAt(),
                announcement.getEndsAt(),
                announcement.getPublishedAt(),
                announcement.getCreatedByAdminId(),
                announcement.getCreatedAt(),
                announcement.getUpdatedAt()
        );
    }
}
