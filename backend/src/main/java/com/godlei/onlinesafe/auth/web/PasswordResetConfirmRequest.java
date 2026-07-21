package com.godlei.onlinesafe.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PasswordResetConfirmRequest(
        @NotBlank(message = "请输入手机号或用户名")
        @Size(max = 64, message = "账号格式不正确")
        String identifier,

        @NotEmpty(message = "请填写全部密保答案")
        @Size(min = 1, max = 3, message = "密保答案数量不正确")
        List<@NotBlank(message = "请填写密保答案") @Size(max = 64) String> answers,

        @NotBlank(message = "请输入新登录密码")
        @Size(min = 8, max = 72, message = "密码长度必须为8至72位")
        String newPassword,

        @NotBlank(message = "请确认新登录密码")
        @Size(min = 8, max = 72, message = "确认密码长度必须为8至72位")
        String confirmPassword
) {
}
