package com.cangshuo.toolbox.tool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import com.cangshuo.toolbox.tool.model.PopularToolResponse;
import com.cangshuo.toolbox.tool.model.PopularToolRow;
import com.cangshuo.toolbox.tool.repository.ToolUsageRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolUsageServiceTest {

    @Test void unknownOrHiddenToolIsRejectedWithoutCounting() {
        var repository = new FakeRepository();
        repository.visible = false;
        var service = new ToolUsageService(repository);
        var failure = assertThrows(ApiException.class, () -> service.record("nope"));
        assertEquals(ApiError.NOT_FOUND, failure.error());
        assertEquals(0, repository.increments);
    }

    @Test void visibleToolIsCountedOnce() {
        var repository = new FakeRepository();
        var service = new ToolUsageService(repository);
        service.record("calculator");
        assertEquals(1, repository.increments);
        assertEquals("calculator", repository.lastCode);
    }

    @Test void popularRowsKeepServerOrderingAndCounts() {
        var repository = new FakeRepository();
        repository.rows = List.of(
                new PopularToolRow("calculator", "计算器", "desc", "calculator", 12L),
                new PopularToolRow("json", "JSON 工具", "desc", "json", 7L));
        var service = new ToolUsageService(repository);
        List<PopularToolResponse> popular = service.popular(8);
        assertEquals(8, repository.lastLimit);
        assertEquals(List.of("calculator", "json"), popular.stream().map(PopularToolResponse::code).toList());
        assertEquals(12L, popular.get(0).useCount());
    }

    private static class FakeRepository extends ToolUsageRepository {
        boolean visible = true;
        int increments = 0;
        String lastCode = "";
        int lastLimit = -1;
        List<PopularToolRow> rows = List.of();

        FakeRepository() { super(null); }

        @Override public boolean visible(String toolCode) { return visible; }
        @Override public void increment(String toolCode) { increments++; lastCode = toolCode; }
        @Override public List<PopularToolRow> findPopular(int limit) { lastLimit = limit; return rows; }
    }
}
