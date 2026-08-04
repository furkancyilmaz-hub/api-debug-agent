package com.furkan.apidebugagent.llm;

public class LlmDisabledException extends LlmException {

    public LlmDisabledException() {
        super("LLM layer is disabled (llm.enabled=false); ask() must not be called");
    }

}