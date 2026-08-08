package com.furkan.apidebugagent.llm;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "llm.provider=anthropic")
class LlmProviderContextTest {

    @Autowired
    private ChatClientLlmClient llmClient;

    @Autowired
    private Map<String, ChatClient> chatClients;

    @Test
    void shouldExposeTheConfiguredProvider() {
        assertThat(llmClient.provider()).isEqualTo(LlmChatClientConfig.ANTHROPIC);
    }

    @Test
    void shouldRegisterOneChatClientPerProvider() {
        assertThat(chatClients).containsKeys(LlmChatClientConfig.ANTHROPIC, LlmChatClientConfig.OPENROUTER);
    }

}
