package com.godlei.onlinesafe.announcement.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record AnnouncementUpsertRequest(
        @NotBlank(message = "请填写公告标题")
        @Size(max = 200, message = "公告标题不能超过 200 字")
        String title,

        @NotBlank(message = "请填写公告正文")
        @Size(max = 10000, message = "公告正文不能超过 10000 字")
        String body,

        boolean pinned,
        Instant startsAt,
        Instant endsAt
) {
}
