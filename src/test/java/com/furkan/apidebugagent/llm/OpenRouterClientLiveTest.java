package com.furkan.apidebugagent.llm;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "llm.provider=openrouter")
@EnabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = ".+")
class OpenRouterClientLiveTest {

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private JsonResponseParser jsonResponseParser;

    @Test
    void shouldCallModelThroughOpenRouterAndReturnParseableJson() {
        assertThat(llmClient.provider()).isEqualTo("openrouter");

        LlmResult result = llmClient.ask("test-echo", Map.of("value", "ping"));

        JsonNode json = jsonResponseParser.parse(result.rawText());

        assertThat(json.get("echo").asText()).isEqualTo("ping");
    }

}
