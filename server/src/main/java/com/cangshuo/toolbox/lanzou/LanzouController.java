package com.cangshuo.toolbox.lanzou;

import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "蓝奏直链解析", description = "解析公开蓝奏云分享页面中的文件信息与临时下载地址。")
@RequestMapping("/api/v1/web-tools/lanzou_direct")
public class LanzouController {
    private final LanzouService service;
    public LanzouController(LanzouService service) { this.service = service; }
    public record ParseRequest(@Size(max = 4096) String text, @Size(max = 64) String password) {}

    @GetMapping("/capabilities")
    @Operation(summary = "读取蓝奏直链解析的可用状态与请求预算")
    public ResponseEntity<ApiResponse<LanzouService.Capabilities>> capabilities(HttpServletRequest request) {
        return ResponseEntity.ok().body(ApiResponse.success(service.capabilities(), TraceIdFilter.traceId(request)));
    }

    @PostMapping(value = "/parse", consumes = "application/json")
    @Operation(summary = "解析蓝奏云公开分享链接",
            description = "输入分享文本和可选密码；密码仅用于本次请求。遇到官方验证页时返回官方下载页，不自动绕过验证。")
    public ResponseEntity<ApiResponse<LanzouService.Result>> parse(@Valid @RequestBody ParseRequest input,
            HttpServletRequest request) {
        String identity = request.getRemoteAddr();
        return ResponseEntity.ok().body(ApiResponse.success(
                service.parse(input.text(), input.password(), identity), TraceIdFilter.traceId(request)));
    }
}
