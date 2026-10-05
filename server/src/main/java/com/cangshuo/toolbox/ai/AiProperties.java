package com.cangshuo.toolbox.ai;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "toolbox.ai")
public record AiProperties(boolean enabled, String endpoint, String apiKey, String model, String tokenParameter,
    int maxOutputTokens, int timeoutSeconds, int dailyAttempts, int maxConcurrent, String providerName) {
    public AiProperties {
        if (endpoint == null) endpoint = "";
        if (apiKey == null) apiKey = "";
        if (model == null) model = "";
        if (tokenParameter == null || tokenParameter.isBlank()) tokenParameter = "max_completion_tokens";
        if (providerName == null || providerName.isBlank()) providerName = "AI provider";
        if (maxOutputTokens == 0) maxOutputTokens = 1024;
        if (timeoutSeconds == 0) timeoutSeconds = 30;
        if (dailyAttempts == 0) dailyAttempts = 20;
        if (maxConcurrent == 0) maxConcurrent = 4;
        if (!tokenParameter.equals("max_tokens") && !tokenParameter.equals("max_completion_tokens")) invalid();
        if (maxOutputTokens < 64 || maxOutputTokens > 4096 || timeoutSeconds < 1 || timeoutSeconds > 60 ||
            dailyAttempts < 1 || dailyAttempts > 1000 || maxConcurrent < 1 || maxConcurrent > 32 ||
            providerName.length() > 64 || providerName.chars().anyMatch(Character::isISOControl)) invalid();
        if (enabled) {
            try {
                URI uri = URI.create(endpoint);
                if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null ||
                    uri.getRawQuery() != null || uri.getRawFragment() != null || !uri.getPath().endsWith("/chat/completions") ||
                    apiKey.isBlank() || apiKey.length() > 512 || apiKey.chars().anyMatch(Character::isISOControl) ||
                    !model.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}")) invalid();
            } catch (IllegalArgumentException error) { invalid(); }
        }
    }
    private static void invalid() { throw new IllegalStateException("Invalid AI configuration (values omitted)"); }
    @Override public String toString() { return "AiProperties[redacted]"; }
}
