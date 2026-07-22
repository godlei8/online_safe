package com.godlei.onlinesafe.auth.web;

import com.godlei.onlinesafe.auth.domain.SmsPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SmsSendRequest(
        @NotBlank(message = "手机号不能为空")
        @Size(max = 32, message = "手机号格式不正确")
        String phone,

        @NotNull(message = "验证码用途不能为空")
        SmsPurpose purpose
) {
}
