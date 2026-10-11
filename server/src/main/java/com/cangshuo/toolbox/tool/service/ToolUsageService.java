package com.cangshuo.toolbox.tool.service;

import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import com.cangshuo.toolbox.tool.model.PopularToolResponse;
import com.cangshuo.toolbox.tool.repository.ToolUsageRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ToolUsageService {

    private final ToolUsageRepository repository;

    public ToolUsageService(ToolUsageRepository repository) {
        this.repository = repository;
    }

    /** Counts one anonymous open of an enabled tool; hidden or unknown codes are rejected. */
    @Transactional
    public void record(String toolCode) {
        if (!repository.visible(toolCode)) {
            throw new ApiException(ApiError.NOT_FOUND);
        }
        repository.increment(toolCode);
    }

    @Transactional(readOnly = true)
    public List<PopularToolResponse> popular(int limit) {
        return repository.findPopular(limit).stream()
                .map(row -> new PopularToolResponse(row.code(), row.name(), row.description(),
                        row.icon(), row.useCount()))
                .toList();
    }
}
