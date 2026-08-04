package com.furkan.apidebugagent.llm;

public class PromptNotFoundException extends LlmException {

    public PromptNotFoundException(String promptName) {
        super("Prompt not found: classpath:prompts/" + promptName + ".st");
    }

}