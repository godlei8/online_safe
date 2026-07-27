package com.godlei.onlinesafe.security;

import tools.jackson.databind.ObjectMapper;
import com.godlei.onlinesafe.audit.application.SecurityAuditService;
import com.godlei.onlinesafe.common.web.ApiError;
import com.godlei.onlinesafe.datarecovery.application.RecentReauthenticationService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;

import java.io.IOException;
import java.time.Instant;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    @Primary
    AuthenticationManager authenticationManager(
            AccountUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    @Qualifier("adminAuthenticationManager")
    AuthenticationManager adminAuthenticationManager(
            AdminUserDetailsService adminUserDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(adminUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    LogoutSuccessHandler userLogoutSuccessHandler(
            SecurityAuditService securityAuditService,
            RecentReauthenticationService reauthenticationService
    ) {
        return (request, response, authentication) -> {
            securityAuditService.recordLogoutUser(authentication, request);
            reauthenticationService.clear(request.getSession(false));
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            SessionRegistry sessionRegistry,
            ObjectMapper objectMapper,
            LogoutSuccessHandler userLogoutSuccessHandler
    ) throws Exception {
        SessionInformationExpiredStrategy expiredSessionStrategy = event ->
                writeSecurityError(
                        event.getResponse(),
                        objectMapper,
                        401,
                        "SESSION_REPLACED",
                        "账号已在其他地方登录，请重新登录",
                        event.getRequest().getRequestURI()
                );

        http
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository)
                        .requireExplicitSave(true))
                .csrf(csrf -> csrf.spa())
                .cors(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/csrf",
                                "/api/auth/session",
                                "/api/auth/registration-policy",
                                "/api/admin/auth/session",
                                "/actuator/health"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/sms/send",
                                "/api/auth/password-reset/confirm",
                                "/api/admin/auth/login",
                                "/api/admin/auth/logout"
                        ).permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/**").hasRole("USER")
                        .anyRequest().denyAll())
                .sessionManagement(session -> session
                        .sessionFixation(fixation -> fixation.changeSessionId())
                        // 上限由 user/admin 各自的 SessionAuthenticationStrategy 在登录时执行；
                        // 这里 -1 仅保留 ConcurrentSessionFilter，用于检测已被标记失效的会话。
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry)
                        .expiredSessionStrategy(expiredSessionStrategy))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies(SurfaceAwareCookieHttpSessionIdResolver.USER_COOKIE)
                        .logoutSuccessHandler(userLogoutSuccessHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeSecurityError(response, objectMapper, 401, "UNAUTHENTICATED", "请先登录", request.getRequestURI()))
                        .accessDeniedHandler((request, response, exception) ->
                                writeSecurityError(response, objectMapper, 403, "ACCESS_DENIED", "没有访问权限", request.getRequestURI())))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny()));

        return http.build();
    }

    private static void writeSecurityError(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            int status,
            String code,
            String message,
            String path
    ) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                new ApiError(Instant.now(), status, code, message, path, null)
        );
    }
}
