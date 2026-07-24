package com.godlei.onlinesafe.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeUsernameRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 32, message = "用户名长度必须为3至32位")
        String username
) {
}
