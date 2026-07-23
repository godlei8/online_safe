package com.godlei.onlinesafe.systemtemplate.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

public record SystemTemplateUpsertRequest(
        @NotBlank(message = "请填写模板名称")
        @Size(max = 128, message = "模板名称不能超过 128 字")
        String name,

        @NotBlank(message = "请填写所属平台")
        @Size(max = 128, message = "所属平台不能超过 128 字")
        String platform,

        @Size(max = 128, message = "渠道名不能超过 128 字")
        String channel,

        @Size(max = 512, message = "渠道网址不能超过 512 字")
        String channelUrl,

        @NotNull(message = "fields 不能为空")
        JsonNode fields,

        Integer sortOrder
) {
}
