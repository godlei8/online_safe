package com.godlei.onlinesafe.auth.application;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class PhoneNormalizer {

    private static final Pattern MAINLAND_CHINA = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern CHINA_WITH_CODE = Pattern.compile("^86(1[3-9]\\d{9})$");
    private static final Pattern NORMALIZED_MAINLAND_CHINA = Pattern.compile("^\\+861[3-9]\\d{9}$");

    public String normalize(String rawPhone) {
        if (rawPhone == null) {
            throw new InvalidRegistrationException("PHONE_FORMAT_INVALID", "手机号格式不正确");
        }

        String value = rawPhone.trim().replaceAll("[\\s()\\-]", "");
        if (value.startsWith("0086")) {
            value = "+86" + value.substring(4);
        } else if (MAINLAND_CHINA.matcher(value).matches()) {
            value = "+86" + value;
        } else if (CHINA_WITH_CODE.matcher(value).matches()) {
            value = "+" + value;
        }

        if (!NORMALIZED_MAINLAND_CHINA.matcher(value).matches()) {
            throw new InvalidRegistrationException("PHONE_FORMAT_INVALID", "手机号格式不正确");
        }
        return value;
    }
}
