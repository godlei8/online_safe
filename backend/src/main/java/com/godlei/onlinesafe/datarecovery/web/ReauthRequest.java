package com.godlei.onlinesafe.datarecovery.web;

import jakarta.validation.constraints.NotBlank;

public record ReauthRequest(
        @NotBlank(message = "请输入当前登录密码")
        String password
) {
}
