package com.cangshuo.toolbox.tool.service;

import com.cangshuo.toolbox.common.response.PageResponse;
import com.cangshuo.toolbox.tool.model.ToolCatalogEntry;
import com.cangshuo.toolbox.tool.model.ToolResponse;
import com.cangshuo.toolbox.tool.repository.ToolCatalogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ToolCatalogService {

    private final ToolCatalogRepository repository;
    private final ObjectMapper objectMapper;

    public ToolCatalogService(ToolCatalogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<ToolResponse> getTools(int page, int pageSize, String categoryCode) {
        long offset = ((long) page - 1L) * pageSize;
        long total = repository.countVisible(categoryCode);
        List<ToolResponse> records = offset >= total ? List.of()
                : repository.findVisible(categoryCode, pageSize, offset).stream().map(this::toResponse).toList();
        return new PageResponse<>(records, page, pageSize, total);
    }

    private ToolResponse toResponse(ToolCatalogEntry entry) {
        return new ToolResponse(entry.code(), entry.name(), entry.description(), entry.categoryCode(),
                entry.icon(), readKeywords(entry.keywordsJson()), entry.mode(), entry.requiresLogin(),
                entry.status(), entry.version(), entry.sortOrder(), entry.featured());
    }

    private List<String> readKeywords(String json) {
        try {
            JsonNode value = objectMapper.readTree(json);
            if (value == null || !value.isArray()) {
                throw invalidKeywords();
            }
            List<String> keywords = new ArrayList<>();
            for (JsonNode keyword : value) {
                if (!keyword.isTextual() || keyword.textValue().isBlank()) {
                    throw invalidKeywords();
                }
                keywords.add(keyword.textValue());
            }
            return List.copyOf(keywords);
        } catch (JsonProcessingException exception) {
            throw invalidKeywords();
        }
    }

    private static DataRetrievalFailureException invalidKeywords() {
        return new DataRetrievalFailureException("Invalid catalog keyword data");
    }
}
