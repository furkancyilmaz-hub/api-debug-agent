package com.furkan.apidebugagent.llm;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenRouterClientTest {

    private static final LlmProperties PROPERTIES = new LlmProperties(true, "openrouter",
            Map.of("openrouter", new LlmProperties.ProviderOptions("openai/gpt-5", 3000)));

    @Mock
    private ObjectProvider<OpenAiChatModel> chatModelProvider;

    @Mock
    private OpenAiChatModel chatModel;

    @Mock
    private PromptLoader promptLoader;

    @Test
    void shouldSendOpenAiChatOptionsWithConfiguredModelAndMaxTokens() {
        OpenRouterClient client = new OpenRouterClient(chatModelProvider, promptLoader, PROPERTIES);
        when(promptLoader.render("test-echo", Map.of("value", "ping"))).thenReturn("rendered prompt");
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
            .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("{\"echo\":\"ping\"}")))));

        LlmResult result = client.ask("test-echo", Map.of("value", "ping"));

        assertThat(result.rawText()).isEqualTo("{\"echo\":\"ping\"}");

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        ChatOptions options = promptCaptor.getValue().getOptions();
        assertThat(options).isInstanceOf(OpenAiChatOptions.class);
        assertThat(options.getModel()).isEqualTo("openai/gpt-5");
        assertThat(options.getMaxTokens()).isEqualTo(3000);
    }

    @Test
    void shouldThrowLlmProviderUnavailableExceptionWhenChatModelBeanIsMissing() {
        OpenRouterClient client = new OpenRouterClient(chatModelProvider, promptLoader, PROPERTIES);
        when(promptLoader.render(any(), any())).thenReturn("rendered prompt");
        when(chatModelProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> client.ask("test-echo", Map.of()))
            .isInstanceOf(LlmProviderUnavailableException.class)
            .hasMessageContaining("openrouter");
    }

}
