package com.furkan.apidebugagent.llm;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmClientTest {

    @Mock
    private ObjectProvider<ChatModel> chatModelProvider;

    @Mock
    private ChatModel chatModel;

    @Mock
    private PromptLoader promptLoader;

    private static final LlmProperties ENABLED_PROPERTIES = new LlmProperties(true, "claude-sonnet-5", 3000);
    private static final LlmProperties DISABLED_PROPERTIES = new LlmProperties(false, "claude-sonnet-5", 3000);

    @Test
    void shouldThrowLlmDisabledExceptionWithoutRequestingChatModelWhenDisabled() {
        LlmClient llmClient = new LlmClient(chatModelProvider, promptLoader, DISABLED_PROPERTIES);

        assertThatThrownBy(() -> llmClient.ask("test-echo", Map.of("value", "ping")))
            .isInstanceOf(LlmDisabledException.class);

        verifyNoInteractions(chatModelProvider);
    }

    @Test
    void shouldSendConfiguredModelAndMaxTokensInChatOptions() {
        LlmClient llmClient = new LlmClient(chatModelProvider, promptLoader, ENABLED_PROPERTIES);
        when(promptLoader.render("test-echo", Map.of("value", "ping"))).thenReturn("rendered prompt");
        when(chatModelProvider.getObject()).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponseWithText("{\"echo\":\"ping\"}"));

        llmClient.ask("test-echo", Map.of("value", "ping"));

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        ChatOptions options = promptCaptor.getValue().getOptions();
        assertThat(options).isInstanceOf(AnthropicChatOptions.class);
        assertThat(options.getModel()).isEqualTo("claude-sonnet-5");
        assertThat(options.getMaxTokens()).isEqualTo(3000);
    }

    @Test
    void shouldThrowLlmResponseExceptionWhenNoGenerationReturned() {
        LlmClient llmClient = new LlmClient(chatModelProvider, promptLoader, ENABLED_PROPERTIES);
        when(promptLoader.render(any(), any())).thenReturn("rendered prompt");
        when(chatModelProvider.getObject()).thenReturn(chatModel);
        ChatResponse emptyResponse = new ChatResponse(java.util.List.of());
        when(chatModel.call(any(Prompt.class))).thenReturn(emptyResponse);

        assertThatThrownBy(() -> llmClient.ask("test-echo", Map.of()))
            .isInstanceOf(LlmResponseException.class);
    }

    @Test
    void shouldThrowLlmResponseExceptionWhenModelReturnsBlankText() {
        LlmClient llmClient = new LlmClient(chatModelProvider, promptLoader, ENABLED_PROPERTIES);
        when(promptLoader.render(any(), any())).thenReturn("rendered prompt");
        when(chatModelProvider.getObject()).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponseWithText(" "));

        assertThatThrownBy(() -> llmClient.ask("test-echo", Map.of()))
            .isInstanceOf(LlmResponseException.class);
    }

    private ChatResponse chatResponseWithText(String text) {
        Generation generation = new Generation(new AssistantMessage(text));
        return new ChatResponse(java.util.List.of(generation));
    }

}
