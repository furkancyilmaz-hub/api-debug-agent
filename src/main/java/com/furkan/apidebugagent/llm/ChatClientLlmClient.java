package com.furkan.apidebugagent.llm;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.util.function.SingletonSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ChatClientLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(ChatClientLlmClient.class);

    private final PromptLoader promptLoader;
    private final LlmProperties llmProperties;
    private final SingletonSupplier<ChatClient> chatClient =
        SingletonSupplier.of(() -> ChatClient.create(resolveChatModel()));

    protected ChatClientLlmClient(PromptLoader promptLoader, LlmProperties llmProperties) {
        this.promptLoader = promptLoader;
        this.llmProperties = llmProperties;
    }

    @Override
    public LlmResult ask(String promptName, Map<String, Object> vars) {
        String rendered = promptLoader.render(promptName, vars);
        log.debug("Calling LLM provider={} prompt={} renderedLength={}", provider(), promptName, rendered.length());

        ChatResponse response = chatClient.obtain()
            .prompt()
            .user(rendered)
            .options(chatOptionsBuilder())
            .call()
            .chatResponse();

        if (response == null) {
            throw new LlmResponseException("Model returned no response for prompt: " + promptName);
        }

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

    protected LlmProperties.ProviderOptions options() {
        return llmProperties.optionsFor(provider());
    }

    protected abstract ChatModel resolveChatModel();

    protected abstract ChatOptions.Builder<?> chatOptionsBuilder();

}
