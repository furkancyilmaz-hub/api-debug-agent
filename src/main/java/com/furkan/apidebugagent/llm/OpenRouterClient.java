package com.furkan.apidebugagent.llm;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class OpenRouterClient extends ChatClientLlmClient {

    public static final String PROVIDER = "openrouter";

    private final ObjectProvider<OpenAiChatModel> chatModelProvider;

    public OpenRouterClient(ObjectProvider<OpenAiChatModel> chatModelProvider, PromptLoader promptLoader,
            LlmProperties llmProperties) {
        super(promptLoader, llmProperties);
        this.chatModelProvider = chatModelProvider;
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    protected ChatModel resolveChatModel() {
        OpenAiChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw new LlmProviderUnavailableException(PROVIDER, "no OpenAiChatModel bean; check spring.ai.openai");
        }
        return chatModel;
    }

    @Override
    protected ChatOptions.Builder<?> chatOptionsBuilder() {
        LlmProperties.ProviderOptions options = options();
        return OpenAiChatOptions.builder().model(options.model()).maxTokens(options.maxTokens());
    }

}
