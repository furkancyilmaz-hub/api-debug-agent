package com.furkan.apidebugagent.llm;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatClientLlmClientTest {

    private static final Map<String, LlmProperties.ProviderOptions> PROVIDERS = Map.of("anthropic",
            new LlmProperties.ProviderOptions("claude-sonnet-5", 3000), "openrouter",
            new LlmProperties.ProviderOptions("openai/gpt-5", 1500));

    @Mock
    private ChatModel anthropicModel;

    @Mock
    private ChatModel openRouterModel;

    @Mock
    private PromptLoader promptLoader;

    @Test
    void shouldCallTheProviderNamedInConfigWithItsOwnOptionsType() {
        when(promptLoader.render("test-echo", Map.of("value", "ping"))).thenReturn("rendered prompt");
        stubModels();

        LlmResult result = client(properties(true, "anthropic")).ask("test-echo", Map.of("value", "ping"));

        assertThat(result.rawText()).isEqualTo("{\"echo\":\"ping\"}");
        verifyNoInteractions(openRouterModel);

        Prompt prompt = capturedPrompt(anthropicModel);
        assertThat(prompt.getInstructions()).singleElement()
            .extracting(message -> message.getText())
            .isEqualTo("rendered prompt");

        ChatOptions options = prompt.getOptions();
        assertThat(options).isInstanceOf(AnthropicChatOptions.class);
        assertThat(options.getModel()).isEqualTo("claude-sonnet-5");
        assertThat(options.getMaxTokens()).isEqualTo(3000);
    }

    @Test
    void shouldReachTheOtherProviderWhenOnlyConfigChanges() {
        when(promptLoader.render(any(), any())).thenReturn("rendered prompt");
        stubModels();

        client(properties(true, "openrouter")).ask("test-echo", Map.of());

        verifyNoInteractions(anthropicModel);

        ChatOptions options = capturedPrompt(openRouterModel).getOptions();
        assertThat(options).isInstanceOf(OpenAiChatOptions.class);
        assertThat(options.getModel()).isEqualTo("openai/gpt-5");
        assertThat(options.getMaxTokens()).isEqualTo(1500);
    }

    @Test
    void shouldMatchProviderNameCaseInsensitively() {
        when(promptLoader.render(any(), any())).thenReturn("rendered prompt");
        stubModels();

        client(properties(true, "OpenRouter")).ask("test-echo", Map.of());

        verify(openRouterModel).call(any(Prompt.class));
    }

    @Test
    void shouldThrowLlmDisabledExceptionWithoutTouchingAnyModel() {
        assertThatThrownBy(() -> client(properties(false, "anthropic")).ask("test-echo", Map.of()))
            .isInstanceOf(LlmDisabledException.class);

        verifyNoInteractions(anthropicModel, openRouterModel, promptLoader);
    }

    @Test
    void shouldThrowUnknownLlmProviderExceptionForUnregisteredProvider() {
        assertThatThrownBy(() -> client(properties(true, "gemini")).ask("test-echo", Map.of()))
            .isInstanceOf(UnknownLlmProviderException.class)
            .hasMessageContaining("gemini")
            .hasMessageContaining("anthropic")
            .hasMessageContaining("openrouter");

        verifyNoInteractions(anthropicModel, openRouterModel, promptLoader);
    }

    @Test
    void shouldThrowLlmProviderUnavailableExceptionWhenTheProviderHasNoModelConfigured() {
        LlmProperties withoutOptions = new LlmProperties(true, "anthropic", Map.of());

        assertThatThrownBy(() -> client(withoutOptions).ask("test-echo", Map.of()))
            .isInstanceOf(LlmProviderUnavailableException.class)
            .hasMessageContaining("llm.providers.anthropic");

        verifyNoInteractions(anthropicModel, promptLoader);
    }

    @Test
    void shouldThrowLlmResponseExceptionWhenNoGenerationReturned() {
        when(promptLoader.render(any(), any())).thenReturn("rendered prompt");
        stubModel(anthropicModel, AnthropicChatOptions.builder().build(), new ChatResponse(List.of()));

        assertThatThrownBy(() -> client(properties(true, "anthropic")).ask("test-echo", Map.of()))
            .isInstanceOf(LlmResponseException.class);
    }

    @Test
    void shouldThrowLlmResponseExceptionWhenModelReturnsBlankText() {
        when(promptLoader.render(any(), any())).thenReturn("rendered prompt");
        stubModel(anthropicModel, AnthropicChatOptions.builder().build(), responseWithText(" "));

        assertThatThrownBy(() -> client(properties(true, "anthropic")).ask("test-echo", Map.of()))
            .isInstanceOf(LlmResponseException.class);
    }

    private ChatClientLlmClient client(LlmProperties llmProperties) {
        Map<String, ChatClient> chatClients = Map.of("anthropic", ChatClient.create(anthropicModel), "openrouter",
                ChatClient.create(openRouterModel));
        return new ChatClientLlmClient(chatClients, promptLoader, llmProperties);
    }

    private static LlmProperties properties(boolean enabled, String provider) {
        return new LlmProperties(enabled, provider, PROVIDERS);
    }

    /** Both models answer; each test asserts which one was actually asked. */
    private void stubModels() {
        stubModelLeniently(anthropicModel, AnthropicChatOptions.builder().build());
        stubModelLeniently(openRouterModel, OpenAiChatOptions.builder().build());
    }

    private void stubModelLeniently(ChatModel chatModel, ChatOptions defaultOptions) {
        lenient().when(chatModel.getOptions()).thenReturn(defaultOptions);
        lenient().when(chatModel.call(any(Prompt.class))).thenReturn(responseWithText("{\"echo\":\"ping\"}"));
    }

    private void stubModel(ChatModel chatModel, ChatOptions defaultOptions, ChatResponse response) {
        when(chatModel.getOptions()).thenReturn(defaultOptions);
        when(chatModel.call(any(Prompt.class))).thenReturn(response);
    }

    private Prompt capturedPrompt(ChatModel chatModel) {
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        return promptCaptor.getValue();
    }

    private ChatResponse responseWithText(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

}
