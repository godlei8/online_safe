package com.godlei.onlinesafe.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.HttpSessionIdResolver;

import java.util.List;

/**
 * 个人端与管理端使用独立 Session Cookie，同一浏览器可并存两套登录态。
 * <ul>
 *   <li>{@code /api/admin/**} → {@link #ADMIN_COOKIE}</li>
 *   <li>其余 API → {@link #USER_COOKIE}</li>
 * </ul>
 */
public final class SurfaceAwareCookieHttpSessionIdResolver implements HttpSessionIdResolver {

    public static final String USER_COOKIE = "ONLINE_SAFE_SESSION";
    public static final String ADMIN_COOKIE = "ONLINE_SAFE_ADMIN_SESSION";

    private final HttpSessionIdResolver userResolver;
    private final HttpSessionIdResolver adminResolver;

    public SurfaceAwareCookieHttpSessionIdResolver(boolean secure, String sameSite) {
        this.userResolver = cookieResolver(USER_COOKIE, secure, sameSite);
        this.adminResolver = cookieResolver(ADMIN_COOKIE, secure, sameSite);
    }

    static boolean isAdminSurface(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String uri = request.getRequestURI();
        String path = contextPath == null || contextPath.isEmpty() || !uri.startsWith(contextPath)
                ? uri
                : uri.substring(contextPath.length());
        return path.startsWith("/api/admin");
    }

    private static HttpSessionIdResolver cookieResolver(String cookieName, boolean secure, String sameSite) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(cookieName);
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(secure);
        if (sameSite != null && !sameSite.isBlank()) {
            serializer.setSameSite(sameSite);
        }
        CookieHttpSessionIdResolver resolver = new CookieHttpSessionIdResolver();
        resolver.setCookieSerializer(serializer);
        return resolver;
    }

    private HttpSessionIdResolver select(HttpServletRequest request) {
        return isAdminSurface(request) ? adminResolver : userResolver;
    }

    @Override
    public List<String> resolveSessionIds(HttpServletRequest request) {
        return select(request).resolveSessionIds(request);
    }

    @Override
    public void setSessionId(HttpServletRequest request, HttpServletResponse response, String sessionId) {
        select(request).setSessionId(request, response, sessionId);
    }

    @Override
    public void expireSession(HttpServletRequest request, HttpServletResponse response) {
        select(request).expireSession(request, response);
    }
}
