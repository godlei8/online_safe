package com.godlei.onlinesafe.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetLookupRequest(
        @NotBlank(message = "请输入手机号或用户名")
        @Size(max = 64, message = "账号格式不正确")
        String identifier
) {
}
