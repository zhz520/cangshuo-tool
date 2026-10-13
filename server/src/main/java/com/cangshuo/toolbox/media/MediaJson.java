package com.cangshuo.toolbox.media;

import com.cangshuo.toolbox.common.exception.*;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Parse serialized page data as JSON, never JavaScript. undefined is accepted only outside strings. */
@Component
public class MediaJson {
    private final ObjectMapper mapper = new ObjectMapper(JsonFactory.builder().streamReadConstraints(
            StreamReadConstraints.builder().maxNestingDepth(64).maxStringLength(200_000).maxNumberLength(100).build()).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public JsonNode json(String text) {
        try { JsonNode value = mapper.readTree(text); if (value == null || value.isNull()) throw missing(); return value; }
        catch (Exception invalid) { throw new ApiException(ApiError.MEDIA_UNAVAILABLE); }
    }
    public JsonNode assignment(String html, String name) {
        var matcher = Pattern.compile("(?:window\\.)?" + Pattern.quote(name) + "\\s*=\\s*").matcher(html);
        if (!matcher.find()) throw missing();
        return json(object(html, matcher.end()));
    }
    public JsonNode script(String html, String id) {
        var matcher = Pattern.compile("<script\\b[^>]*\\bid=[\"']" + Pattern.quote(id) + "[\"'][^>]*>", Pattern.CASE_INSENSITIVE).matcher(html);
        if (!matcher.find()) throw missing();
        return json(object(html, matcher.end()));
    }
    static String object(String html, int start) {
        while (start < html.length() && Character.isWhitespace(html.charAt(start))) start++;
        if (start >= html.length() || html.charAt(start) != '{' && html.charAt(start) != '[') throw missing();
        int depth = 0; boolean quote = false, escape = false;
        var out = new StringBuilder();
        for (int i = start; i < html.length(); i++) {
            char c = html.charAt(i);
            if (quote) {
                out.append(c);
                if (escape) escape = false; else if (c == '\\') escape = true; else if (c == '"') quote = false;
                continue;
            }
            if (c == '"') { quote = true; out.append(c); continue; }
            if (c == '{' || c == '[') { if (++depth > 64) throw missing(); }
            if (c == '}' || c == ']') depth--;
            if (html.startsWith("undefined", i)
                    && (i == start || ":,[ \t\r\n".indexOf(html.charAt(i - 1)) >= 0)
                    && (i + 9 >= html.length() || ",]} \t\r\n".indexOf(html.charAt(i + 9)) >= 0)) {
                out.append("null"); i += 8;
            } else out.append(c);
            if (depth == 0) return out.toString();
        }
        throw missing();
    }
    private static ApiException missing() { return new ApiException(ApiError.MEDIA_UNAVAILABLE); }
}
