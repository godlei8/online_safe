package com.godlei.onlinesafe.auth.web;

public record PasswordResetQuestionResponse(
        String questionId,
        String questionType,
        String questionCode,
        String questionText,
        int sortOrder
) {
}
