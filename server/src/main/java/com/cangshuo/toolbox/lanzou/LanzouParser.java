package com.cangshuo.toolbox.lanzou;

import com.cangshuo.toolbox.common.exception.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/** Parses the two public page variants reviewed in hanximeng/LanzouAPI and WhY15w/lanzou-url-parser. */
@Component
public class LanzouParser {
    public enum State { DIRECT, OFFICIAL_PAGE }
    public record Parsed(State state, URI shareUri, String fileName, String fileSize, URI downloadPage,
                         URI directUri, boolean verificationRequired, boolean rateLimited) {}

    private static final Pattern FILE_NAME = Pattern.compile(
            "<div[^>]*class=[\"'][^\"']*n_box_3fn[^\"']*[\"'][^>]*>(.*?)</div>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern FILE_NAME_COPY = Pattern.compile(
            "<div[^>]*class=[\"']b[\"'][^>]*>\\s*<span>(.*?)</span>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern FILE_NAME_JS = Pattern.compile("var\\s+filename\\s*=\\s*['\"](.*?)['\"]", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern FILE_SIZE = Pattern.compile(
            "<div[^>]*class=[\"'][^\"']*n_filesize[^\"']*[\"'][^>]*>(?:大小[:：]\\s*)?(.*?)</div>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern FILE_SIZE_OLD = Pattern.compile("<span[^>]*class=[\"']p7[\"'][^>]*>文件大小[:：]</span>(.*?)<br>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern IFRAME = Pattern.compile("<iframe[^>]*\\ssrc=[\"']/?([^\"']+)[\"']", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern AJAX = Pattern.compile(
            "(?:https?://[A-Za-z0-9.-]+)?/ajax(?:m|file)\\.php\\?file=\\d+", Pattern.CASE_INSENSITIVE);
    private static final Pattern SIGN_LITERAL = Pattern.compile("['\"]sign['\"]\\s*:\\s*(['\"])(.*?)\\1", Pattern.DOTALL);
    private static final Pattern SIGN_REFERENCE = Pattern.compile("['\"]sign['\"]\\s*:\\s*([A-Za-z_$][\\w$]*)");
    private static final Pattern SIGN_VARIABLE = Pattern.compile("var\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*(['\"])(.*?)\\2", Pattern.DOTALL);
    private static final Pattern AJAX_DATA = Pattern.compile("ajaxdata\\s*=\\s*['\"](.*?)['\"]", Pattern.DOTALL);
    private static final Pattern WP_SIGN = Pattern.compile("wp_sign\\s*=\\s*['\"](.*?)['\"]", Pattern.DOTALL);
    private static final Pattern ARG1 = Pattern.compile("var\\s+arg1\\s*=.*?", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final LanzouHttp http;
    private final ObjectMapper json;

    @org.springframework.beans.factory.annotation.Autowired
    public LanzouParser(LanzouHttp http, ObjectMapper json) { this.http = http; this.json = json; }

    public Parsed parse(LanzouInput input, long deadline) {
        var page = http.get(input.uri(), "", input.uri().toString(), deadline);
        String html = page.body();
        verifyPage(html);
        if (removed(html)) throw new ApiException(ApiError.LANZOU_FILE_UNAVAILABLE);
        String fileName = first(html, FILE_NAME, FILE_NAME_COPY, FILE_NAME_JS);
        if (fileName.isBlank()) {
            String title = first(html, TITLE).replaceAll("(?i)\\s*[-_·]\\s*蓝奏.*$", "").strip();
            if (!title.isBlank() && !title.contains("蓝奏云")) fileName = title;
        }
        String fileSize = first(html, FILE_SIZE, FILE_SIZE_OLD);
        String clean = withoutComments(html);
        String ajax = last(clean, AJAX);

        JsonNode result;
        if (passwordPage(html)) {
            if (input.password().isEmpty()) throw new ApiException(ApiError.LANZOU_PASSWORD_REQUIRED);
            String sign = sign(clean);
            if (sign.isBlank() || ajax.isBlank()) throw provider();
            URI endpoint = endpoint(page.uri(), ajax);
            Map<String, String> form = new LinkedHashMap<>();
            form.put("action", "downprocess"); form.put("sign", sign); form.put("p", input.password()); form.put("kd", "1");
            result = ajax(endpoint, form, page.cookies(), page.uri(), deadline);
        } else {
            URI detailUri = page.uri();
            String detail = clean;
            String iframe = first(clean, IFRAME);
            if (!iframe.isBlank()) {
                detailUri = endpoint(page.uri(), iframe);
                var frame = http.get(detailUri, page.cookies(), page.uri().toString(), deadline);
                verifyPage(frame.body());
                if (removed(frame.body())) throw new ApiException(ApiError.LANZOU_FILE_UNAVAILABLE);
                detail = withoutComments(frame.body());
                ajax = last(detail, AJAX);
                page = new LanzouHttp.Document(detailUri, detail, frame.cookies());
            }
            if (ajax.isBlank()) {
                if (folder(html)) throw new ApiException(ApiError.LANZOU_FOLDER_UNSUPPORTED);
                throw new ApiException(ApiError.LANZOU_VERIFICATION_REQUIRED);
            }
            String sign = sign(detail);
            String websign = first(detail, AJAX_DATA);
            if (sign.isBlank()) sign = first(detail, WP_SIGN);
            List<String> signs = all(detail, AJAX_DATA);
            if (websign.isBlank() && !signs.isEmpty()) websign = signs.get(signs.size() - 1);
            if (sign.isBlank()) throw provider();
            Map<String, String> form = new LinkedHashMap<>();
            form.put("action", "downprocess");
            if (!websign.isBlank()) { form.put("websignkey", websign); form.put("signs", websign); }
            form.put("sign", sign); form.put("websign", ""); form.put("kd", "1"); form.put("ves", "1");
            result = ajax(endpoint(detailUri, ajax), form, page.cookies(), detailUri, deadline);
        }

        int zt = result.path("zt").asInt(-1);
        if (zt != 1) classifyAjax(result.path("inf").asText(""));
        if (fileName.isBlank()) fileName = result.path("inf").asText("").strip();
        URI downloadPage = LanzouHosts.downloadPage(result.path("dom").asText(), result.path("url").asText());
        var probe = http.probe(downloadPage, page.cookies(), "https://developer.lanzoug.com/", deadline);
        if (probe.location() != null) {
            try {
                URI direct = LanzouHosts.direct(probe.location().toString());
                return new Parsed(State.DIRECT, input.uri(), safeText(fileName), safeText(fileSize), downloadPage,
                        direct, false, false);
            } catch (ApiException invalid) {
                // A non-http or malformed redirect is still safer to expose as the official page.
            }
        }
        boolean verification = challenge(probe.body());
        boolean limited = probe.status() == 403 || probe.status() == 429;
        return new Parsed(State.OFFICIAL_PAGE, input.uri(), safeText(fileName), safeText(fileSize), downloadPage,
                null, verification, limited);
    }

    private JsonNode ajax(URI endpoint, Map<String, String> fields, String cookies, URI referer, long deadline) {
        String form = fields.entrySet().stream().map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
        var response = http.post(endpoint, form, cookies, referer.toString(), deadline);
        verifyPage(response.body());
        try {
            JsonNode node = json.readTree(response.body());
            return node != null && node.isObject() ? node : json.createObjectNode();
        } catch (Exception error) {
            throw provider();
        }
    }

    private static void classifyAjax(String message) {
        String value = message == null ? "" : message;
        if (value.contains("密码") || value.toLowerCase(Locale.ROOT).contains("password"))
            throw new ApiException(ApiError.LANZOU_PASSWORD_INVALID);
        if (value.contains("频繁") || value.contains("限制") || value.contains("稍后"))
            throw new ApiException(ApiError.LANZOU_RESTRICTED);
        if (value.contains("验证") || value.contains("安全"))
            throw new ApiException(ApiError.LANZOU_VERIFICATION_REQUIRED);
        if (value.contains("取消") || value.contains("不存在") || value.contains("失效"))
            throw new ApiException(ApiError.LANZOU_FILE_UNAVAILABLE);
        throw provider();
    }

    private static URI endpoint(URI base, String raw) {
        try {
            String value = raw.strip();
            URI uri = value.startsWith("http://") || value.startsWith("https://")
                    ? URI.create(value) : base.resolve(value.startsWith("/") ? value : "/" + value);
            if ("http".equalsIgnoreCase(uri.getScheme())) uri = URI.create("https:" + uri.toString().substring(5));
            return LanzouHosts.ownedPage(uri);
        } catch (ApiException error) { throw error; }
        catch (IllegalArgumentException error) { throw provider(); }
    }

    private static String sign(String html) {
        Matcher inline = SIGN_LITERAL.matcher(html);
        if (inline.find() && !inline.group(2).isBlank()) return inline.group(2);
        Matcher reference = SIGN_REFERENCE.matcher(html);
        if (!reference.find()) return "";
        String name = reference.group(1);
        String found = "";
        Matcher variables = SIGN_VARIABLE.matcher(html);
        while (variables.find()) if (variables.group(1).equals(name) && !variables.group(3).isBlank()) found = variables.group(3);
        return found;
    }

    private static String withoutComments(String html) {
        return html.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)^\\s*//.*$", "");
    }

    private static boolean passwordPage(String html) {
        return html.contains("function down_p()") || html.contains("id=\"pwd\"")
                || html.contains("id='pwd'") || html.contains("inputpwd");
    }
    private static boolean removed(String html) {
        return html.contains("文件取消分享了") || html.contains("文件不存在") || html.contains("链接失效")
                || html.contains("分享已取消");
    }
    private static boolean folder(String html) {
        return html.contains("class=\"file-list") || html.contains("id=\"filelist\"")
                || html.contains("文件夹") && html.contains("文件数目");
    }
    private static boolean challenge(String html) {
        return html != null && (html.contains("acw_sc__v2") || ARG1.matcher(html).find()
                || html.contains("aliyun_waf") || html.contains("安全验证") || html.contains("访问验证"));
    }
    private static void verifyPage(String html) {
        if (challenge(html)) throw new ApiException(ApiError.LANZOU_VERIFICATION_REQUIRED);
    }

    private static String safeText(String value) {
        String text = HtmlUtils.htmlUnescape(value == null ? "" : value).replaceAll("\\s+", " ").strip();
        return text.length() <= 500 ? text : text.substring(0, 500);
    }
    private static String first(String html, Pattern... patterns) {
        if (html == null) return "";
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(html);
            if (matcher.find() && matcher.groupCount() > 0 && !matcher.group(1).isBlank()) return safeText(matcher.group(1));
        }
        return "";
    }
    private static List<String> all(String html, Pattern pattern) {
        List<String> values = new ArrayList<>();
        Matcher matcher = pattern.matcher(html);
        while (matcher.find() && values.size() < 20) if (!matcher.group(1).isBlank()) values.add(matcher.group(1));
        return values;
    }
    private static String last(String html, Pattern pattern) {
        Matcher matcher = pattern.matcher(html);
        String found = ""; int count = 0;
        while (matcher.find() && count++ < 40) found = matcher.group();
        return found;
    }
    private static String encode(String value) { return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8); }
    private static ApiException provider() { return new ApiException(ApiError.LANZOU_PROVIDER_ERROR); }
}
