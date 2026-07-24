package com.godlei.onlinesafe.audit.infrastructure;

public record ClientContext(
        String ipMasked,
        String ipFingerprint,
        String browserFamily,
        String osFamily,
        String deviceType,
        String routeTemplate,
        String httpMethod,
        String requestId
) {
}
