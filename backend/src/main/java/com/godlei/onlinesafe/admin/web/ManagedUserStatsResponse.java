package com.godlei.onlinesafe.admin.web;

public record ManagedUserStatsResponse(
        long total,
        long active,
        long disabled,
        long activeLast7Days
) {
}
