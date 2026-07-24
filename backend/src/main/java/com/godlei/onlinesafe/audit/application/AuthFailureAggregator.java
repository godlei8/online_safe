package com.godlei.onlinesafe.audit.application;

import com.godlei.onlinesafe.audit.infrastructure.AuditProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 认证失败防洪：同一聚合键每分钟前 N 次逐条落库，超出部分在窗口结束写聚合事件。
 */
@Component
public class AuthFailureAggregator {

    public enum Decision {
        WRITE_EVENT,
        SKIP,
        WRITE_AGGREGATE
    }

    private record Window(long minuteEpoch, AtomicInteger count, AtomicInteger suppressed) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int limit;
    private final Clock clock;

    public AuthFailureAggregator(AuditProperties properties, Clock clock) {
        this.limit = properties.failureAggregateLimitPerMinute();
        this.clock = clock;
    }

    public Decision decide(String aggregateKey, boolean alwaysWriteUntilLimit) {
        try {
            long minute = clock.instant().getEpochSecond() / 60;
            Window window = windows.compute(aggregateKey, (key, existing) -> {
                if (existing == null || existing.minuteEpoch != minute) {
                    return new Window(minute, new AtomicInteger(0), new AtomicInteger(0));
                }
                return existing;
            });
            int current = window.count.incrementAndGet();
            if (current <= limit) {
                return Decision.WRITE_EVENT;
            }
            int suppressed = window.suppressed.incrementAndGet();
            // 窗口内首次超限时写聚合事件；之后跳过
            if (suppressed == 1) {
                return Decision.WRITE_AGGREGATE;
            }
            if (alwaysWriteUntilLimit) {
                return Decision.SKIP;
            }
            return Decision.SKIP;
        } catch (RuntimeException exception) {
            return Decision.WRITE_EVENT;
        }
    }

    public int totalInCurrentWindow(String aggregateKey) {
        long minute = clock.instant().getEpochSecond() / 60;
        Window window = windows.get(aggregateKey);
        if (window == null || window.minuteEpoch != minute) {
            return 0;
        }
        return window.count.get();
    }
}
