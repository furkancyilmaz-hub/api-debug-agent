package com.furkan.apidebugagent.llm;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The only way to the model. Picks the provider {@code llm.provider} names out of the registered
 * {@link ChatClient} beans, renders the prompt from a file and validates what comes back. No other
 * class touches a {@code ChatClient} or a {@code ChatModel}.
 *
 * <p>Which provider and which model are both config: swapping either takes no code change.
 */
@Component
public class ChatClientLlmClient {

    private static final Logger log = LoggerFactory.getLogger(ChatClientLlmClient.class);

    /** Lower-cased finish reasons that mean "the budget ran out", across providers. */
    private static final Set<String> TRUNCATION_REASONS = Set.of("length", "max_tokens");

    /** Provider name (bean name, lower-cased) to its client. */
    private final Map<String, ChatClient> chatClients;

    private final PromptLoader promptLoader;

    private final LlmProperties llmProperties;

    public ChatClientLlmClient(Map<String, ChatClient> chatClients, PromptLoader promptLoader,
            LlmProperties llmProperties) {
        this.chatClients = normalize(chatClients);
        this.promptLoader = promptLoader;
        this.llmProperties = llmProperties;
        log.info("LLM providers registered={} active={} enabled={}", this.chatClients.keySet(),
            llmProperties.provider(), llmProperties.enabled());
    }

    /** The provider {@code llm.provider} points at, registered or not. */
    public String provider() {
        return llmProperties.provider();
    }

    public LlmResult ask(String promptName, Map<String, Object> vars) {
        if (!llmProperties.enabled()) {
            throw new LlmDisabledException();
        }

        String provider = provider();
        ChatClient chatClient = chatClient(provider);
        LlmProperties.ProviderOptions options = llmProperties.optionsFor(provider);

        String rendered = promptLoader.render(promptName, vars);
        log.debug("Calling LLM provider={} model={} prompt={} renderedLength={}", provider, options.model(), promptName,
            rendered.length());

        // A plain ChatOptions builder is enough: the request is assembled on top of the model's own
        // options builder, so the built options keep the provider's type and only model and
        // maxTokens are overwritten from here.
        ChatResponse response = chatClient.prompt()
            .user(rendered)
            .options(ChatOptions.builder().model(options.model()).maxTokens(options.maxTokens()))
            .call()
            .chatResponse();

        if (response == null) {
            throw new LlmResponseException("Model returned no response for prompt: " + promptName);
        }

        Generation result = response.getResult();
        if (result == null) {
            throw new LlmResponseException("Model returned no generation for prompt: " + promptName);
        }

        // Before the text is looked at: a model that ran out of budget returns a fragment, and a
        // fragment fails later as "invalid JSON", which names the symptom instead of the cause.
        String finishReason = result.getMetadata().getFinishReason();
        if (truncated(finishReason)) {
            throw new LlmResponseException("Model response was truncated (finishReason=" + finishReason + ", provider="
                    + provider + ", model=" + options.model() + ", maxTokens=" + options.maxTokens() + "); raise llm.providers."
                    + LlmProperties.normalize(provider) + ".max-tokens");
        }

        String text = result.getOutput().getText();
        if (text == null || text.isBlank()) {
            throw new LlmResponseException("Model returned blank text for prompt: " + promptName);
        }

        return new LlmResult(text, finishReason);
    }

    /** Every provider spells it differently; OpenAI-compatible says {@code length}, Anthropic {@code max_tokens}. */
    private static boolean truncated(String finishReason) {
        return finishReason != null && TRUNCATION_REASONS.contains(finishReason.toLowerCase(Locale.ROOT));
    }

    private ChatClient chatClient(String provider) {
        ChatClient chatClient = chatClients.get(LlmProperties.normalize(provider));
        if (chatClient == null) {
            throw new UnknownLlmProviderException(provider, chatClients.keySet());
        }
        return chatClient;
    }

    private static Map<String, ChatClient> normalize(Map<String, ChatClient> chatClients) {
        Map<String, ChatClient> normalized = new LinkedHashMap<>();
        chatClients.forEach((name, chatClient) -> {
            if (normalized.put(LlmProperties.normalize(name), chatClient) != null) {
                throw new IllegalStateException("Duplicate ChatClient provider: " + LlmProperties.normalize(name));
            }
        });
        return Map.copyOf(normalized);
    }

}
