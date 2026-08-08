package com.furkan.apidebugagent.llm;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnthropicClientTest {

    private static final LlmProperties PROPERTIES = new LlmProperties(true, "anthropic",
            Map.of("anthropic", new LlmProperties.ProviderOptions("claude-sonnet-5", 3000)));

    @Mock
    private ObjectProvider<AnthropicChatModel> chatModelProvider;

    @Mock
    private AnthropicChatModel chatModel;

    @Mock
    private PromptLoader promptLoader;

    @Test
    void shouldSendRenderedPromptWithConfiguredModelAndMaxTokens() {
        AnthropicClient client = new AnthropicClient(chatModelProvider, promptLoader, PROPERTIES);
        when(promptLoader.render("test-echo", Map.of("value", "ping"))).thenReturn("rendered prompt");
        stubChatModel(chatResponseWithText("{\"echo\":\"ping\"}"));

        LlmResult result = client.ask("test-echo", Map.of("value", "ping"));

        assertThat(result.rawText()).isEqualTo("{\"echo\":\"ping\"}");

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        Prompt prompt = promptCaptor.getValue();
        assertThat(prompt.getInstructions()).singleElement()
            .extracting(message -> message.getText())
            .isEqualTo("rendered prompt");

        ChatOptions options = prompt.getOptions();
        assertThat(options).isInstanceOf(AnthropicChatOptions.class);
        assertThat(options.getModel()).isEqualTo("claude-sonnet-5");
        assertThat(options.getMaxTokens()).isEqualTo(3000);
    }

    @Test
    void shouldThrowLlmResponseExceptionWhenNoGenerationReturned() {
        AnthropicClient client = new AnthropicClient(chatModelProvider, promptLoader, PROPERTIES);
        when(promptLoader.render(any(), any())).thenReturn("rendered prompt");
        stubChatModel(new ChatResponse(List.of()));

        assertThatThrownBy(() -> client.ask("test-echo", Map.of())).isInstanceOf(LlmResponseException.class);
    }

    @Test
    void shouldThrowLlmResponseExceptionWhenModelReturnsBlankText() {
        AnthropicClient client = new AnthropicClient(chatModelProvider, promptLoader, PROPERTIES);
        when(promptLoader.render(any(), any())).thenReturn("rendered prompt");
        stubChatModel(chatResponseWithText(" "));

        assertThatThrownBy(() -> client.ask("test-echo", Map.of())).isInstanceOf(LlmResponseException.class);
    }

    @Test
    void shouldThrowLlmProviderUnavailableExceptionWhenChatModelBeanIsMissing() {
        AnthropicClient client = new AnthropicClient(chatModelProvider, promptLoader, PROPERTIES);
        when(promptLoader.render(any(), any())).thenReturn("rendered prompt");
        when(chatModelProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> client.ask("test-echo", Map.of()))
            .isInstanceOf(LlmProviderUnavailableException.class)
            .hasMessageContaining("anthropic");
    }

    @Test
    void shouldThrowLlmProviderUnavailableExceptionWhenModelIsNotConfigured() {
        LlmProperties withoutModel = new LlmProperties(true, "anthropic", Map.of());
        AnthropicClient client = new AnthropicClient(chatModelProvider, promptLoader, withoutModel);
        when(promptLoader.render(any(), any())).thenReturn("rendered prompt");
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);

        assertThatThrownBy(() -> client.ask("test-echo", Map.of()))
            .isInstanceOf(LlmProviderUnavailableException.class)
            .hasMessageContaining("llm.providers.anthropic");
    }

    private void stubChatModel(ChatResponse response) {
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);
        when(chatModel.getOptions()).thenReturn(AnthropicChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(response);
    }

    private ChatResponse chatResponseWithText(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

}
