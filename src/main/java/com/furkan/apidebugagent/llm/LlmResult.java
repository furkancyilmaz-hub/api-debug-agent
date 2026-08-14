package com.furkan.apidebugagent.llm;

/**
 * What one model call produced. {@code finishReason} is the provider's own word for why the model
 * stopped; it is kept only so a failure can name its cause — it never reaches the report.
 */
public record LlmResult(String rawText, String finishReason) {

    public LlmResult(String rawText) {
        this(rawText, null);
    }

}
