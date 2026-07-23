package com.godlei.onlinesafe.systemtemplate.application;

import com.godlei.onlinesafe.systemtemplate.domain.SystemTemplate;
import com.godlei.onlinesafe.systemtemplate.domain.SystemTemplateStatus;
import com.godlei.onlinesafe.systemtemplate.infrastructure.SystemTemplateRepository;
import com.godlei.onlinesafe.systemtemplate.web.SystemTemplateAdminResponse;
import com.godlei.onlinesafe.systemtemplate.web.SystemTemplateSortRequest;
import com.godlei.onlinesafe.systemtemplate.web.SystemTemplateUpsertRequest;
import com.godlei.onlinesafe.systemtemplate.web.SystemTemplateUserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

@Service
public class SystemTemplateService {

    private final SystemTemplateRepository repository;
    private final ObjectMapper objectMapper;

    public SystemTemplateService(SystemTemplateRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Page<SystemTemplateAdminResponse> listAdmin(
            SystemTemplateStatus status,
            String platform,
            String q,
            Pageable pageable
    ) {
        String platformFilter = blankToNull(platform);
        String qFilter = blankToNull(q);
        return repository.searchAdmin(status, platformFilter, qFilter, pageable)
                .map(item -> SystemTemplateAdminResponse.from(item, objectMapper));
    }

    @Transactional(readOnly = true)
    public SystemTemplateAdminResponse getAdmin(String id) {
        return SystemTemplateAdminResponse.from(require(id), objectMapper);
    }

    @Transactional
    public SystemTemplateAdminResponse create(String adminId, SystemTemplateUpsertRequest request) {
        String payloadJson = buildPayload(request);
        int sortOrder = request.sortOrder() == null ? 0 : request.sortOrder();
        SystemTemplate saved = repository.save(SystemTemplate.create(
                request.name().trim(),
                request.platform().trim(),
                nullToEmpty(request.channel()),
                nullToEmpty(request.channelUrl()),
                payloadJson,
                sortOrder,
                adminId
        ));
        return SystemTemplateAdminResponse.from(saved, objectMapper);
    }

    @Transactional
    public SystemTemplateAdminResponse update(String id, String adminId, SystemTemplateUpsertRequest request) {
        SystemTemplate template = require(id);
        template.updateContent(
                request.name().trim(),
                request.platform().trim(),
                nullToEmpty(request.channel()),
                nullToEmpty(request.channelUrl()),
                buildPayload(request),
                request.sortOrder(),
                adminId
        );
        return SystemTemplateAdminResponse.from(repository.save(template), objectMapper);
    }

    @Transactional
    public SystemTemplateAdminResponse publish(String id, String adminId) {
        SystemTemplate template = require(id);
        if (template.getStatus() == SystemTemplateStatus.PUBLISHED) {
            throw new InvalidSystemTemplateOperationException("SYSTEM_TEMPLATE_INVALID_STATUS", "模板已发布");
        }
        template.publish(Instant.now());
        template.updateSortOrder(template.getSortOrder(), adminId);
        return SystemTemplateAdminResponse.from(repository.save(template), objectMapper);
    }

    @Transactional
    public SystemTemplateAdminResponse offline(String id, String adminId) {
        SystemTemplate template = require(id);
        if (template.getStatus() != SystemTemplateStatus.PUBLISHED) {
            throw new InvalidSystemTemplateOperationException("SYSTEM_TEMPLATE_INVALID_STATUS", "仅已发布模板可下线");
        }
        template.offline();
        template.updateSortOrder(template.getSortOrder(), adminId);
        return SystemTemplateAdminResponse.from(repository.save(template), objectMapper);
    }

    @Transactional
    public SystemTemplateAdminResponse updateSort(String id, String adminId, SystemTemplateSortRequest request) {
        SystemTemplate template = require(id);
        template.updateSortOrder(request.sortOrder(), adminId);
        return SystemTemplateAdminResponse.from(repository.save(template), objectMapper);
    }

    @Transactional
    public void deleteDraft(String id) {
        SystemTemplate template = require(id);
        if (template.getStatus() != SystemTemplateStatus.DRAFT) {
            throw new InvalidSystemTemplateOperationException("SYSTEM_TEMPLATE_INVALID_STATUS", "仅草稿模板可删除");
        }
        repository.delete(template);
    }

    @Transactional(readOnly = true)
    public List<SystemTemplateUserResponse> listPublished(String platform, String q) {
        return repository.findPublished(blankToNull(platform), blankToNull(q)).stream()
                .map(item -> SystemTemplateUserResponse.from(item, objectMapper))
                .toList();
    }

    @Transactional(readOnly = true)
    public SystemTemplateUserResponse getPublished(String id) {
        SystemTemplate template = repository.findByIdAndStatus(id, SystemTemplateStatus.PUBLISHED)
                .orElseThrow(SystemTemplateNotFoundException::new);
        return SystemTemplateUserResponse.from(template, objectMapper);
    }

    private String buildPayload(SystemTemplateUpsertRequest request) {
        return SystemTemplatePayloadSupport.buildPayloadJson(
                objectMapper,
                request.name().trim(),
                request.platform().trim(),
                nullToEmpty(request.channel()),
                nullToEmpty(request.channelUrl()),
                request.fields()
        );
    }

    private SystemTemplate require(String id) {
        return repository.findById(id).orElseThrow(SystemTemplateNotFoundException::new);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
