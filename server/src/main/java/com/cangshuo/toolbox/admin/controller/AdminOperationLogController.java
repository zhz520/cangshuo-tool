package com.cangshuo.toolbox.admin.controller;

import com.cangshuo.toolbox.admin.model.AdminOperationLogRow;
import com.cangshuo.toolbox.admin.service.AdminOperationLogService;
import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.response.ApiResponse;
import com.cangshuo.toolbox.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/admin/logs", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Admin operation logs")
public class AdminOperationLogController {
    private final AdminOperationLogService service;
    public AdminOperationLogController(AdminOperationLogService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Page through administrator audit rows; module/result accept uppercase codes")
    public ApiResponse<PageResponse<AdminOperationLogRow>> list(
            @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(name = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(name = "module", required = false) String module,
            @RequestParam(name = "result", required = false) String result,
            @RequestParam(name = "keyword", required = false) String keyword,
            HttpServletRequest request) {
        return ApiResponse.success(service.list(page, pageSize, module, result, keyword),
                TraceIdFilter.traceId(request));
    }
}
