package com.godlei.onlinesafe.admin.web;

import com.godlei.onlinesafe.admin.domain.InvitePurpose;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateInvitationRequest(
        /** 用途类型；缺省为用户注册 */
        InvitePurpose purpose,

        @NotNull(message = "请设置最大使用次数")
        @Min(value = 1, message = "最大使用次数至少为 1")
        @Max(value = 10000, message = "最大使用次数过大")
        Integer maxUses,

        Instant expiresAt,

        @Size(max = 200, message = "备注不能超过 200 字")
        String note
) {
}
