package com.cangshuo.toolbox.zepp;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** The upstream protocol material stays in server configuration, never in the page or APK. */
@ConfigurationProperties(prefix = "toolbox.zepp")
public record ZeppProperties(boolean enabled, String loginKey, String loginIv) {
    public boolean ready() {
        return enabled && valid(loginKey) && valid(loginIv);
    }
    private static boolean valid(String value) {
        return value != null && value.getBytes(StandardCharsets.UTF_8).length == 16;
    }
    @Override public String toString() { return "ZeppProperties[enabled=" + enabled + ", protocol=redacted]"; }
}
