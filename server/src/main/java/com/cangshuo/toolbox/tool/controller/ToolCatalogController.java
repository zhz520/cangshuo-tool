package com.cangshuo.toolbox.tool.controller;

import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.response.ApiResponse;
import com.cangshuo.toolbox.common.response.PageResponse;
import com.cangshuo.toolbox.tool.model.ToolResponse;
import com.cangshuo.toolbox.tool.service.ToolCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Tools", description = "公开工具目录元数据")
@RestController
@RequestMapping("/api/v1/tools")
public class ToolCatalogController {

    private final ToolCatalogService service;

    public ToolCatalogController(ToolCatalogService service) {
        this.service = service;
    }

    @Operation(operationId = "getTools", summary = "分页获取启用工具",
            description = "匿名访问；只返回未删除的启用工具及启用分类，按 sortOrder、code 升序排列。"
                    + "不存在的分类或越界页返回空 records；不返回配置或可执行代码。", responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "工具目录页",
                    headers = @Header(name = "X-Trace-Id", schema = @Schema(type = "string", pattern = "^[0-9a-f]{32}$"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "10001：参数无效",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)),
                    headers = @Header(name = "X-Trace-Id", schema = @Schema(type = "string", pattern = "^[0-9a-f]{32}$"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "10008：数据库暂不可用",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)),
                    headers = @Header(name = "X-Trace-Id", schema = @Schema(type = "string", pattern = "^[0-9a-f]{32}$"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "10000：目录数据或服务错误",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)),
                    headers = @Header(name = "X-Trace-Id", schema = @Schema(type = "string", pattern = "^[0-9a-f]{32}$")))
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<PageResponse<ToolResponse>> getTools(
            @Parameter(description = "页码；省略或空值时为 1", schema = @Schema(minimum = "1", maximum = "2147483647"))
            @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页条数；省略或空值时为 20", schema = @Schema(minimum = "1", maximum = "100"))
            @RequestParam(name = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @Parameter(description = "精确分类编码；格式合法但不存在时返回空页", example = "CALC",
                    schema = @Schema(pattern = "^[A-Z][A-Z0-9_]{0,31}$", maxLength = 32))
            @RequestParam(name = "categoryCode", required = false)
            @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,31}$") String categoryCode,
            HttpServletRequest request) {
        return ApiResponse.success(service.getTools(page, pageSize, categoryCode), TraceIdFilter.traceId(request));
    }
}
