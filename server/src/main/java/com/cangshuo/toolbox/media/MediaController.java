package com.cangshuo.toolbox.media;

import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "短视频与图集解析", description = "短视频、图文图集、封面与 Live Photo；下载使用解析结果提供的临时句柄。")
@RequestMapping("/api/v1/web-tools/short_video_parse")
public class MediaController {
    private final MediaService service;
    public MediaController(MediaService service) { this.service = service; }
    public record ParseRequest(@NotBlank @Size(max = 4096) String text) {}
    @GetMapping("/capabilities")
    @Operation(summary = "读取短视频解析支持的平台与处理上限")
    public ResponseEntity<ApiResponse<MediaService.Capabilities>> capabilities(HttpServletRequest request) {
        // MediaConfiguration's body-budget filter owns the no-store header for this whole prefix.
        return ResponseEntity.ok().body(ApiResponse.success(service.capabilities(), TraceIdFilter.traceId(request)));
    }
    @PostMapping(value = "/parse", consumes = "application/json")
    @Operation(summary = "解析公开分享文本中的视频、图集与封面", description = "输入分享文本；输出临时结果 id 和资源句柄，不支持任意地址代理。")
    public ResponseEntity<ApiResponse<MediaService.Result>> parse(@Valid @RequestBody ParseRequest input, HttpServletRequest request) {
        return ResponseEntity.ok().body(ApiResponse.success(service.parse(input.text(), identity(request)), TraceIdFilter.traceId(request)));
    }
    @DeleteMapping("/results/{id}")
    @Operation(summary = "清除一次解析结果及其临时下载句柄")
    public ResponseEntity<ApiResponse<Void>> clear(@PathVariable String id, HttpServletRequest request) {
        service.clear(id);
        return ResponseEntity.ok().body(ApiResponse.success(null, TraceIdFilter.traceId(request)));
    }
    @GetMapping("/assets/{id}/{index}")
    @Operation(summary = "预览或下载解析后的单个媒体资源", description = "index 来自解析结果；download=true 以附件下载。支持 Range 断点请求；过期结果返回 410。")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "完整文件；Content-Type 随媒体格式变化",
                content = @Content(mediaType = "application/octet-stream", schema = @Schema(type = "string", format = "binary"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "206", description = "Range 指定的部分文件；响应含 Content-Range",
                content = @Content(mediaType = "application/octet-stream", schema = @Schema(type = "string", format = "binary")))
    })
    public ResponseEntity<StreamingResponseBody> asset(@PathVariable String id, @PathVariable int index,
            @RequestParam(defaultValue = "false") boolean download, @RequestHeader(value = "Range", required = false) String range) {
        var prepared = service.transfer(id, index, range);
        var source = prepared.download();
        var headers = headers(source.contentType(), prepared.fileName(), download);
        headers.set("Accept-Ranges", "bytes");
        if (source.length() >= 0) headers.setContentLength(source.length());
        if (source.contentRange() != null) headers.set("Content-Range", source.contentRange());
        return ResponseEntity.status(source.status()).headers(headers).body(output -> {
            try (prepared) { source.writeTo(output, MediaHttp.MAX_FILE_BYTES); }
            catch (Exception error) { throw new IOException("Media transfer interrupted"); }
        });
    }
    @GetMapping("/albums/{id}")
    @Operation(summary = "将选中的图集资源打包为 ZIP", description = "selection 为逗号分隔的资源索引；仅接受当前结果中的图片，受数量和体积预算限制。")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "图片 ZIP 压缩包",
            content = @Content(mediaType = "application/zip", schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<StreamingResponseBody> album(@PathVariable String id, @RequestParam String selection, HttpServletRequest request) {
        var archive = service.album(id, selection, identity(request));
        var headers = headers("application/zip", archive.fileName(), true);
        headers.setContentLength(archive.bytes().length);
        return ResponseEntity.ok().headers(headers).body(output -> { try (archive) { output.write(archive.bytes()); } });
    }
    private static HttpHeaders headers(String type, String file, boolean attachment) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(type));
        headers.set("X-Content-Type-Options", "nosniff"); headers.set("Referrer-Policy", "no-referrer");
        headers.setContentDisposition((attachment ? ContentDisposition.attachment() : ContentDisposition.inline()).filename(file).build());
        return headers;
    }
    private static String identity(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        return remote == null || remote.length() > 64 ? "unknown" : remote;
    }
}
