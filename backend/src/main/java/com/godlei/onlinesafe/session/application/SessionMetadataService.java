package com.godlei.onlinesafe.session.application;

import com.godlei.onlinesafe.audit.infrastructure.ClientContext;
import com.godlei.onlinesafe.audit.infrastructure.ClientContextResolver;
import com.godlei.onlinesafe.session.domain.SessionMetadataKeys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class SessionMetadataService {

    private final ClientContextResolver clientContextResolver;
    private final Clock clock;

    public SessionMetadataService(ClientContextResolver clientContextResolver, Clock clock) {
        this.clientContextResolver = clientContextResolver;
        this.clock = clock;
    }

    public void writeOnLogin(HttpServletRequest request, HttpSession session) {
        if (session == null) {
            return;
        }
        ClientContext client = clientContextResolver.resolve(request);
        session.setAttribute(SessionMetadataKeys.PUBLIC_ID, UUID.randomUUID().toString());
        session.setAttribute(SessionMetadataKeys.BROWSER_FAMILY, safe(client.browserFamily(), "Unknown"));
        session.setAttribute(SessionMetadataKeys.OS_FAMILY, safe(client.osFamily(), "Unknown"));
        session.setAttribute(SessionMetadataKeys.DEVICE_TYPE, safe(client.deviceType(), "UNKNOWN"));
        session.setAttribute(SessionMetadataKeys.IP_MASKED, safe(client.ipMasked(), "unknown"));
        session.setAttribute(SessionMetadataKeys.LOGIN_AT, clock.instant().toString());
    }

    public String ensurePublicId(Session session) {
        String existing = session.getAttribute(SessionMetadataKeys.PUBLIC_ID);
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        String created = UUID.randomUUID().toString();
        session.setAttribute(SessionMetadataKeys.PUBLIC_ID, created);
        return created;
    }

    public String readString(Session session, String key, String fallback) {
        Object value = session.getAttribute(key);
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    public Instant readInstant(Session session, String key) {
        Object value = session.getAttribute(key);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String safe(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
