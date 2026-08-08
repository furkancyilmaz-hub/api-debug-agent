package com.furkan.apidebugagent.llm;

public class LlmProviderUnavailableException extends LlmException {

    public LlmProviderUnavailableException(String provider, String reason) {
        super("LLM provider is not usable: " + provider + "; " + reason);
    }

}
