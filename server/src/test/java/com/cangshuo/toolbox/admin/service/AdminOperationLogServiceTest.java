package com.cangshuo.toolbox.admin.service;

import com.cangshuo.toolbox.admin.model.AdminOperationLogRow;
import com.cangshuo.toolbox.admin.repository.AdminOperationLogRepository;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminOperationLogServiceTest {
    private final AdminOperationLogRepository logs = mock(AdminOperationLogRepository.class);
    private final AdminOperationLogService service = new AdminOperationLogService(logs);

    @Test void listNormalizesFiltersAndReturnsRows() {
        when(logs.count("TOOL", "SUCCESS", null)).thenReturn(1L);
        when(logs.find("TOOL", "SUCCESS", null, 20, 0L)).thenReturn(List.of(new AdminOperationLogRow(9L, 3L, "root",
                "tool", "CREATE", "/api/v1/admin/tools", "POST", "203.0.113.20", "SUCCESS",
                Instant.parse("2026-10-05T08:00:00Z"))));
        var page = service.list(1, 20, " tool ", "success", null);
        assertEquals(1, page.records().size());
        assertEquals("CREATE", page.records().get(0).operation());
        assertEquals("root", page.records().get(0).adminUsername());
    }

    @Test void emptyFiltersReturnEmptyPagesPastTheEnd() {
        when(logs.count(null, null, null)).thenReturn(0L);
        assertEquals(0, service.list(1, 20, null, null, null).records().size());
        assertEquals(0, service.list(4, 20, null, null, null).records().size());
    }

    @Test void invalidFiltersAreRejected() {
        assertThrows(ApiException.class, () -> service.list(1, 20, "bad module", null, null));
        assertThrows(ApiException.class, () -> service.list(1, 20, "A".repeat(33), null, null));
        assertThrows(ApiException.class, () -> service.list(1, 20, null, "bad result", null));
        assertEquals(ApiError.INVALID_ARGUMENT,
                assertThrows(ApiException.class, () -> service.list(1, 20, null, null, "x".repeat(129))).error());
        verifyNoInteractions(logs);
    }
}
