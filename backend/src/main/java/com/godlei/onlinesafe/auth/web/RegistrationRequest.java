package com.godlei.onlinesafe.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
        @NotBlank(message = "手机号不能为空")
        @Size(max = 32, message = "手机号格式不正确")
        String phone,

        @NotBlank(message = "短信验证码不能为空")
        @Size(min = 4, max = 8, message = "短信验证码格式不正确")
        String smsCode,

        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 32, message = "用户名长度必须为3至32位")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 72, message = "密码长度必须为8至72位")
        String password,

        @NotBlank(message = "确认密码不能为空")
        @Size(min = 8, max = 72, message = "确认密码长度必须为8至72位")
        String confirmPassword
) {
}
