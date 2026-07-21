package com.godlei.onlinesafe.auth.web;

import java.util.List;

public record PasswordResetLookupResponse(
        List<PasswordResetQuestionResponse> questions
) {
}
