package com.cangshuo.toolbox.tool.controller;

import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.response.ApiResponse;
import com.cangshuo.toolbox.tool.model.PopularToolResponse;
import com.cangshuo.toolbox.tool.service.ToolUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "工具目录与排行", description = "公开工具目录元数据")
@RestController
@RequestMapping("/api/v1/tools")
public class ToolUsageController {

    private final ToolUsageService service;

    public ToolUsageController(ToolUsageService service) {
        this.service = service;
    }

    @Operation(operationId = "recordToolUsage", summary = "记录一次工具打开（匿名聚合计数，不保存用户标识）")
    @PostMapping(value = "/{code}/usage", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Void> record(
            @Parameter(description = "工具编码", example = "calculator",
                    schema = @Schema(pattern = "^[a-z][a-z0-9_]{0,63}$", maxLength = 64))
            @PathVariable("code") @Pattern(regexp = "^[a-z][a-z0-9_]{0,63}$") String code,
            HttpServletRequest request) {
        service.record(code);
        return ApiResponse.<Void>success(null, TraceIdFilter.traceId(request));
    }

    @Operation(operationId = "getPopularTools", summary = "按使用量降序获取热门工具")
    @GetMapping(value = "/popular", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<List<PopularToolResponse>> popular(
            @Parameter(description = "返回条数；省略或空值时为 10", schema = @Schema(minimum = "1", maximum = "50"))
            @RequestParam(name = "limit", defaultValue = "10") @Min(1) @Max(50) int limit,
            HttpServletRequest request) {
        return ApiResponse.success(service.popular(limit), TraceIdFilter.traceId(request));
    }
}
