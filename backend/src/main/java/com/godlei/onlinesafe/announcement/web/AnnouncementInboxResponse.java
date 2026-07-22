package com.godlei.onlinesafe.announcement.web;

public record AnnouncementInboxResponse(
        int unreadCount,
        AnnouncementUserResponse latestUnread,
        AnnouncementUserResponse pinned
) {
}
