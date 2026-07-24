package com.godlei.onlinesafe.audit.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
public class ClientContextResolver {

    private final AuditProperties properties;
    private final AuditFingerprintService fingerprintService;

    public ClientContextResolver(AuditProperties properties, AuditFingerprintService fingerprintService) {
        this.properties = properties;
        this.fingerprintService = fingerprintService;
    }

    public ClientContext resolve(HttpServletRequest request) {
        if (request == null) {
            return new ClientContext(null, null, "Unknown", "Unknown", "UNKNOWN", null, null, null);
        }
        String remote = request.getRemoteAddr();
        String clientIp = resolveClientIp(remote, request);
        String ua = request.getHeader("User-Agent");
        String browser = detectBrowser(ua);
        String os = detectOs(ua);
        String device = detectDevice(ua);
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String route = pattern == null ? request.getRequestURI() : String.valueOf(pattern);
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        return new ClientContext(
                maskIp(clientIp),
                fingerprintService.hmacSha256Hex(clientIp),
                browser,
                os,
                device,
                route,
                request.getMethod(),
                requestId
        );
    }

    private String resolveClientIp(String remote, HttpServletRequest request) {
        if (remote != null && isTrustedProxy(remote)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            String fwd = request.getHeader("Forwarded");
            if (fwd != null && fwd.toLowerCase(Locale.ROOT).contains("for=")) {
                int idx = fwd.toLowerCase(Locale.ROOT).indexOf("for=");
                String part = fwd.substring(idx + 4).split("[;,]")[0].trim().replace("\"", "");
                if (part.startsWith("[")) {
                    int end = part.indexOf(']');
                    if (end > 0) {
                        return part.substring(1, end);
                    }
                }
                return part.split(":")[0];
            }
        }
        return remote == null ? "unknown" : remote;
    }

    private boolean isTrustedProxy(String remote) {
        Set<String> trusted = Set.copyOf(properties.trustedProxyList());
        if (trusted.isEmpty()) {
            // 未配置时：仅信任回环与常见 docker 网关段，避免伪造 XFF
            return remote.startsWith("127.") || remote.equals("::1") || remote.startsWith("172.") || remote.startsWith("10.");
        }
        return trusted.contains(remote);
    }

    static String maskIp(String ip) {
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            return "unknown";
        }
        if (ip.contains(":")) {
            String[] parts = ip.split(":");
            StringBuilder sb = new StringBuilder();
            int keep = Math.min(4, parts.length);
            for (int i = 0; i < keep; i++) {
                if (i > 0) sb.append(':');
                sb.append(parts[i].isBlank() ? "0" : parts[i]);
            }
            return sb + "::/64";
        }
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + "." + parts[2] + ".*";
        }
        return "masked";
    }

    static String detectBrowser(String ua) {
        if (ua == null) return "Unknown";
        String lower = ua.toLowerCase(Locale.ROOT);
        if (lower.contains("edg/")) return "Edge";
        if (lower.contains("chrome/")) return "Chrome";
        if (lower.contains("firefox/")) return "Firefox";
        if (lower.contains("safari/") && !lower.contains("chrome/")) return "Safari";
        return "Other";
    }

    static String detectOs(String ua) {
        if (ua == null) return "Unknown";
        String lower = ua.toLowerCase(Locale.ROOT);
        if (lower.contains("windows")) return "Windows";
        if (lower.contains("android")) return "Android";
        if (lower.contains("iphone") || lower.contains("ipad") || lower.contains("ios")) return "iOS";
        if (lower.contains("mac os") || lower.contains("macintosh")) return "macOS";
        if (lower.contains("linux")) return "Linux";
        return "Other";
    }

    static String detectDevice(String ua) {
        if (ua == null) return "UNKNOWN";
        String lower = ua.toLowerCase(Locale.ROOT);
        if (lower.contains("ipad") || lower.contains("tablet")) return "TABLET";
        if (lower.contains("mobi") || lower.contains("iphone") || lower.contains("android")) return "MOBILE";
        return "DESKTOP";
    }
}
