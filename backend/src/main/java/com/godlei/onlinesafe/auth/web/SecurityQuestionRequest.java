package com.godlei.onlinesafe.auth.web;

import com.godlei.onlinesafe.auth.domain.SecurityQuestionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SecurityQuestionRequest(
        @NotNull(message = "请选择密保问题类型")
        SecurityQuestionType questionType,

        @Size(max = 64, message = "内置题编码过长")
        String questionCode,

        @Size(max = 200, message = "自定义问题过长")
        String questionText,

        @NotBlank(message = "请填写密保答案")
        @Size(min = 1, max = 64, message = "密保答案长度为 1 至 64 位")
        String answer
) {
}
