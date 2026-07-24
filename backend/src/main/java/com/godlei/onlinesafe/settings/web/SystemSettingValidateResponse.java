package com.godlei.onlinesafe.settings.web;

import java.util.List;

public record SystemSettingValidateResponse(
        boolean valid,
        String highestRisk,
        List<String> effects,
        String confirmationTitle
) {
}
