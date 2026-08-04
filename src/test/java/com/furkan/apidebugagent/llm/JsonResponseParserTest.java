package com.furkan.apidebugagent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonResponseParserTest {

    private final JsonResponseParser parser = new JsonResponseParser();

    @Test
    void shouldParsePlainJson() {
        JsonNode node = parser.parse("{\"echo\":\"ping\"}");

        assertThat(node.get("echo").asText()).isEqualTo("ping");
    }

    @Test
    void shouldParseJsonWrappedInJsonCodeFence() {
        JsonNode node = parser.parse("```json\n{\"echo\":\"ping\"}\n```");

        assertThat(node.get("echo").asText()).isEqualTo("ping");
    }

    @Test
    void shouldParseJsonWrappedInBareCodeFence() {
        JsonNode node = parser.parse("```\n{\"echo\":\"ping\"}\n```");

        assertThat(node.get("echo").asText()).isEqualTo("ping");
    }

    @Test
    void shouldThrowLlmJsonParseExceptionWhenTextIsNotJson() {
        assertThatThrownBy(() -> parser.parse("not json at all"))
            .isInstanceOf(LlmJsonParseException.class)
            .hasMessageContaining("not json at all");
    }

}
