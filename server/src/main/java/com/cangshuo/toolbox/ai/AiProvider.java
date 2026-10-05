package com.cangshuo.toolbox.ai;

public interface AiProvider {
    AiResponse complete(String instruction, String input);
}
