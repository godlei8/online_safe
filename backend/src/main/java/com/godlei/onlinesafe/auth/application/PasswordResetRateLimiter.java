package com.godlei.onlinesafe.auth.application;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简单内存限流：按标识在窗口内限制尝试次数（进程内，重启清空）。
 */
@Component
public class PasswordResetRateLimiter {

    private static final int MAX_ATTEMPTS = 8;
    private static final long WINDOW_SECONDS = 15 * 60;

    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public PasswordResetRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void check(String key) {
        Instant now = clock.instant();
        Window window = windows.compute(key, (ignored, existing) -> {
            if (existing == null || existing.expiresAt().isBefore(now)) {
                return new Window(1, now.plusSeconds(WINDOW_SECONDS));
            }
            return new Window(existing.attempts() + 1, existing.expiresAt());
        });
        if (window.attempts() > MAX_ATTEMPTS) {
            throw new PasswordResetException("PASSWORD_RESET_RATE_LIMITED", "尝试过于频繁，请稍后再试");
        }
    }

    public void clear(String key) {
        windows.remove(key);
    }

    private record Window(int attempts, Instant expiresAt) {
    }
}
