package com.furkan.apidebugagent.llm;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One named {@link ChatClient} bean per provider; the bean name is the name {@code llm.provider}
 * selects. Adding a provider means adding a bean here plus an {@code llm.providers.<name>} entry —
 * {@link ChatClientLlmClient} stays untouched.
 */
@Configuration
public class LlmChatClientConfig {

    public static final String ANTHROPIC = "anthropic";

    public static final String OPENROUTER = "openrouter";

    @Bean(ANTHROPIC)
    ChatClient anthropicChatClient(AnthropicChatModel chatModel) {
        return ChatClient.create(chatModel);
    }

    /**
     * OpenRouter speaks the OpenAI-compatible chat completions API, so it rides on the OpenAI
     * model; the address and the key come from {@code spring.ai.openai}.
     */
    @Bean(OPENROUTER)
    ChatClient openRouterChatClient(OpenAiChatModel chatModel) {
        return ChatClient.create(chatModel);
    }

}
