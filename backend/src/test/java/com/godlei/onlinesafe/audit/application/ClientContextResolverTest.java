package com.godlei.onlinesafe.audit.application;

import com.godlei.onlinesafe.audit.infrastructure.AuditFingerprintService;
import com.godlei.onlinesafe.audit.infrastructure.AuditProperties;
import com.godlei.onlinesafe.audit.infrastructure.ClientContext;
import com.godlei.onlinesafe.audit.infrastructure.ClientContextResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientContextResolverTest {

    @Test
    void ignoresForwardedForWhenRemoteIsNotTrustedProxy() {
        AuditProperties properties = new AuditProperties("test-key", "127.0.0.1", 5, 100);
        ClientContextResolver resolver = new ClientContextResolver(
                properties,
                new AuditFingerprintService(properties)
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.1");
        request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0");

        ClientContext context = resolver.resolve(request);

        assertThat(context.ipMasked()).isEqualTo("203.0.113.*");
        assertThat(context.browserFamily()).isEqualTo("Chrome");
        assertThat(context.osFamily()).isEqualTo("Windows");
        assertThat(context.deviceType()).isEqualTo("DESKTOP");
    }

    @Test
    void rejectsUnknownMetadataKeys() {
        AuditMetadataPolicy policy = new AuditMetadataPolicy();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                policy.sanitize(
                        com.godlei.onlinesafe.audit.domain.AuditEventType.USER_LOGIN_SUCCEEDED,
                        java.util.Map.of("password", "secret")
                )
        );
    }
}
