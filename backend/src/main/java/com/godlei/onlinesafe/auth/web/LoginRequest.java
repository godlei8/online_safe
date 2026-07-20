package com.godlei.onlinesafe.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "登录账号不能为空")
        @Size(max = 64, message = "登录账号格式不正确")
        String identifier,

        @NotBlank(message = "密码不能为空")
        @Size(max = 72, message = "密码格式不正确")
        String password
) {
}
