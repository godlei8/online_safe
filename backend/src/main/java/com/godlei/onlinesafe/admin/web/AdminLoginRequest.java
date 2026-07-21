package com.godlei.onlinesafe.admin.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminLoginRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(max = 64, message = "用户名格式不正确")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(max = 72, message = "密码格式不正确")
        String password
) {
}
