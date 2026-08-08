package com.furkan.apidebugagent.llm;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class AnthropicClient extends ChatClientLlmClient {

    public static final String PROVIDER = "anthropic";

    private final ObjectProvider<AnthropicChatModel> chatModelProvider;

    public AnthropicClient(ObjectProvider<AnthropicChatModel> chatModelProvider, PromptLoader promptLoader,
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
        AnthropicChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw new LlmProviderUnavailableException(PROVIDER, "no AnthropicChatModel bean; check spring.ai.anthropic");
        }
        return chatModel;
    }

    @Override
    protected ChatOptions.Builder<?> chatOptionsBuilder() {
        LlmProperties.ProviderOptions options = options();
        return AnthropicChatOptions.builder().model(options.model()).maxTokens(options.maxTokens());
    }

}
