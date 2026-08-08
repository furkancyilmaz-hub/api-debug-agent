package com.furkan.apidebugagent.llm;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LlmProviderContextTest {

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private List<LlmClient> allClients;

    @Test
    void shouldInjectRouterAsThePrimaryLlmClient() {
        assertThat(llmClient).isInstanceOf(RoutingLlmClient.class);
        assertThat(llmClient.provider()).isEqualTo("anthropic");
    }

    @Test
    void shouldRegisterEveryProviderImplementation() {
        assertThat(allClients).hasAtLeastOneElementOfType(AnthropicClient.class)
            .hasAtLeastOneElementOfType(OpenRouterClient.class)
            .hasAtLeastOneElementOfType(RoutingLlmClient.class);
    }

}
