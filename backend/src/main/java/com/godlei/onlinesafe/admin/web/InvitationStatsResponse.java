package com.godlei.onlinesafe.admin.web;

public record InvitationStatsResponse(
        long total,
        long active,
        long expiringSoon,
        long disabledOrExhausted
) {
}
