package com.godlei.onlinesafe.admin.application;

import org.springframework.stereotype.Component;

@Component
public class PhoneMasker {

    public String mask(String phone) {
        if (phone == null || phone.isBlank()) {
            return "—";
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.startsWith("86") && digits.length() >= 13) {
            digits = digits.substring(2);
        }
        if (digits.length() < 7) {
            return "***";
        }
        if (digits.length() >= 11) {
            return digits.substring(0, 3) + "****" + digits.substring(digits.length() - 4);
        }
        return digits.substring(0, 2) + "****" + digits.substring(digits.length() - 2);
    }
}
