package com.godlei.onlinesafe.systemtemplate.web;

import jakarta.validation.constraints.NotNull;

public record SystemTemplateSortRequest(
        @NotNull(message = "请填写排序值")
        Integer sortOrder
) {
}
