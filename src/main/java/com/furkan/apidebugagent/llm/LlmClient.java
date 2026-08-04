package com.furkan.apidebugagent.llm;

import java.util.Map;

import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final PromptLoader promptLoader;
    private final LlmProperties llmProperties;

    public LlmClient(ObjectProvider<ChatModel> chatModelProvider, PromptLoader promptLoader,
            LlmProperties llmProperties) {
        this.chatModelProvider = chatModelProvider;
        this.promptLoader = promptLoader;
        this.llmProperties = llmProperties;
    }

    public LlmResult ask(String promptName, Map<String, Object> vars) {
        if (!llmProperties.enabled()) {
            throw new LlmDisabledException();
        }

        String rendered = promptLoader.render(promptName, vars);
        log.debug("Calling LLM with prompt={} renderedLength={}", promptName, rendered.length());

        AnthropicChatOptions options = AnthropicChatOptions.builder()
            .model(llmProperties.model())
            .maxTokens(llmProperties.maxTokens())
            .build();

        ChatModel chatModel = chatModelProvider.getObject();
        ChatResponse response = chatModel.call(new Prompt(rendered, options));

        Generation result = response.getResult();
        if (result == null) {
            throw new LlmResponseException("Model returned no generation for prompt: " + promptName);
        }

        String text = result.getOutput().getText();
        if (text == null || text.isBlank()) {
            throw new LlmResponseException("Model returned blank text for prompt: " + promptName);
        }

        return new LlmResult(text);
    }

}