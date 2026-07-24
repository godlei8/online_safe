package com.godlei.onlinesafe.audit.application;

import org.springframework.stereotype.Component;

@Component
public class IdentifierMasker {

    public String maskLoginIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return "—";
        }
        String trimmed = identifier.trim();
        String digits = trimmed.replaceAll("\\D", "");
        if (looksLikePhone(digits)) {
            if (digits.startsWith("86") && digits.length() >= 13) {
                digits = digits.substring(2);
            }
            if (digits.length() >= 11) {
                return digits.substring(0, 3) + "****" + digits.substring(digits.length() - 4);
            }
        }
        return maskUsername(trimmed);
    }

    public String maskUsername(String username) {
        if (username == null || username.isBlank()) {
            return "—";
        }
        String value = username.trim();
        if (value.length() <= 2) {
            return value.charAt(0) + "*";
        }
        if (value.length() <= 4) {
            return value.charAt(0) + "**" + value.charAt(value.length() - 1);
        }
        return value.substring(0, 2) + "***" + value.substring(value.length() - 1);
    }

    private static boolean looksLikePhone(String digits) {
        return digits.length() >= 7 && digits.chars().allMatch(Character::isDigit);
    }
}
