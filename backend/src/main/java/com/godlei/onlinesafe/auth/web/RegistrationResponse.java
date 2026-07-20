package com.godlei.onlinesafe.auth.web;

import com.godlei.onlinesafe.auth.domain.AppUser;

import java.time.Instant;

public record RegistrationResponse(
        String id,
        String username,
        String maskedPhone,
        boolean phoneVerified,
        Instant createdAt
) {
    public static RegistrationResponse from(AppUser user) {
        return new RegistrationResponse(
                user.getId(),
                user.getUsername(),
                maskPhone(user.getPhone()),
                user.isPhoneVerified(),
                user.getCreatedAt()
        );
    }

    private static String maskPhone(String phone) {
        if (phone.length() <= 4) {
            return "****";
        }
        return "****" + phone.substring(phone.length() - 4);
    }
}
