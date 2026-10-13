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

@RestController
@RequestMapping("/api/v1/web-tools/short_video_parse")
public class MediaController {
    private final MediaService service;
    public MediaController(MediaService service) { this.service = service; }
    public record ParseRequest(@NotBlank @Size(max = 4096) String text) {}
    @GetMapping("/capabilities")
    public ResponseEntity<ApiResponse<MediaService.Capabilities>> capabilities(HttpServletRequest request) {
        // MediaConfiguration's body-budget filter owns the no-store header for this whole prefix.
        return ResponseEntity.ok().body(ApiResponse.success(service.capabilities(), TraceIdFilter.traceId(request)));
    }
    @PostMapping(value = "/parse", consumes = "application/json")
    public ResponseEntity<ApiResponse<MediaService.Result>> parse(@Valid @RequestBody ParseRequest input, HttpServletRequest request) {
        return ResponseEntity.ok().body(ApiResponse.success(service.parse(input.text(), identity(request)), TraceIdFilter.traceId(request)));
    }
    @DeleteMapping("/results/{id}")
    public ResponseEntity<ApiResponse<Void>> clear(@PathVariable String id, HttpServletRequest request) {
        service.clear(id);
        return ResponseEntity.ok().body(ApiResponse.success(null, TraceIdFilter.traceId(request)));
    }
    @GetMapping("/assets/{id}/{index}")
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
