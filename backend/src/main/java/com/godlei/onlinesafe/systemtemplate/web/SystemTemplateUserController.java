package com.godlei.onlinesafe.systemtemplate.web;

import com.godlei.onlinesafe.systemtemplate.application.SystemTemplateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/system-templates")
public class SystemTemplateUserController {

    private final SystemTemplateService systemTemplateService;

    public SystemTemplateUserController(SystemTemplateService systemTemplateService) {
        this.systemTemplateService = systemTemplateService;
    }

    @GetMapping
    public List<SystemTemplateUserResponse> list(
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String q
    ) {
        return systemTemplateService.listPublished(platform, q);
    }

    @GetMapping("/{id}")
    public SystemTemplateUserResponse get(@PathVariable String id) {
        return systemTemplateService.getPublished(id);
    }
}
