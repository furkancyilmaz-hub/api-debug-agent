package com.furkan.apidebugagent.llm;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingLlmClientTest {

    private final StubLlmClient anthropic = new StubLlmClient("anthropic", "from anthropic");
    private final StubLlmClient openrouter = new StubLlmClient("openrouter", "from openrouter");

    @Test
    void shouldDelegateToConfiguredProvider() {
        RoutingLlmClient router = router(properties(true, "anthropic"));

        assertThat(router.ask("test-echo", Map.of()).rawText()).isEqualTo("from anthropic");
        assertThat(anthropic.calls).isEqualTo(1);
        assertThat(openrouter.calls).isZero();
    }

    @Test
    void shouldDelegateToOtherProviderWhenOnlyConfigChanges() {
        RoutingLlmClient router = router(properties(true, "openrouter"));

        assertThat(router.ask("test-echo", Map.of()).rawText()).isEqualTo("from openrouter");
        assertThat(anthropic.calls).isZero();
        assertThat(openrouter.calls).isEqualTo(1);
    }

    @Test
    void shouldMatchProviderNameCaseInsensitively() {
        RoutingLlmClient router = router(properties(true, "OpenRouter"));

        assertThat(router.ask("test-echo", Map.of()).rawText()).isEqualTo("from openrouter");
    }

    @Test
    void shouldThrowLlmDisabledExceptionWithoutTouchingDelegates() {
        RoutingLlmClient router = router(properties(false, "anthropic"));

        assertThatThrownBy(() -> router.ask("test-echo", Map.of())).isInstanceOf(LlmDisabledException.class);
        assertThat(anthropic.calls).isZero();
        assertThat(openrouter.calls).isZero();
    }

    @Test
    void shouldThrowUnknownLlmProviderExceptionForUnregisteredProvider() {
        RoutingLlmClient router = router(properties(true, "gemini"));

        assertThatThrownBy(() -> router.ask("test-echo", Map.of()))
            .isInstanceOf(UnknownLlmProviderException.class)
            .hasMessageContaining("gemini")
            .hasMessageContaining("anthropic")
            .hasMessageContaining("openrouter");
    }

    @Test
    void shouldRejectTwoClientsClaimingTheSameProvider() {
        List<LlmClient> clients = List.of(anthropic, new StubLlmClient("anthropic", "duplicate"));

        assertThatThrownBy(() -> new RoutingLlmClient(clients, properties(true, "anthropic")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("anthropic");
    }

    private RoutingLlmClient router(LlmProperties llmProperties) {
        return new RoutingLlmClient(List.of(anthropic, openrouter), llmProperties);
    }

    private static LlmProperties properties(boolean enabled, String provider) {
        return new LlmProperties(enabled, provider, Map.of());
    }

    private static final class StubLlmClient implements LlmClient {

        private final String provider;
        private final String rawText;
        private int calls;

        private StubLlmClient(String provider, String rawText) {
            this.provider = provider;
            this.rawText = rawText;
        }

        @Override
        public String provider() {
            return provider;
        }

        @Override
        public LlmResult ask(String promptName, Map<String, Object> vars) {
            calls++;
            return new LlmResult(rawText);
        }

    }

}
