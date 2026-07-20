package com.godlei.onlinesafe.auth.application;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class UsernameNormalizer {

    private static final Pattern ALLOWED = Pattern.compile("^[\\p{L}\\p{N}_-]{3,32}$");

    public UsernameValue normalizeForRegistration(String rawUsername) {
        String display = rawUsername == null ? "" : rawUsername.trim();
        if (!ALLOWED.matcher(display).matches()) {
            throw new InvalidRegistrationException(
                    "USERNAME_FORMAT_INVALID",
                    "用户名只能包含文字、数字、下划线或连字符，长度为3至32位"
            );
        }
        return new UsernameValue(display, display.toLowerCase(Locale.ROOT));
    }

    public String normalizeForLogin(String rawUsername) {
        return rawUsername == null ? "" : rawUsername.trim().toLowerCase(Locale.ROOT);
    }

    public record UsernameValue(String display, String normalized) {
    }
}
