package com.cangshuo.toolbox.ai;

public record AiResponse(String text, String model, boolean truncated, Long inputTokens, Long outputTokens) {
    @Override public String toString() { return "AiResponse[redacted]"; }
}
