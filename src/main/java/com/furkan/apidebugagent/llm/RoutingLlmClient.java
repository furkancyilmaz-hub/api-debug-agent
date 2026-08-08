package com.furkan.apidebugagent.llm;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Primary
@Component
public class RoutingLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(RoutingLlmClient.class);

    private final Map<String, LlmClient> delegates;
    private final LlmProperties llmProperties;

    public RoutingLlmClient(List<LlmClient> clients, LlmProperties llmProperties) {
        this.delegates = clients.stream()
            .filter(client -> client != this)
            .collect(Collectors.collectingAndThen(
                    Collectors.toMap(client -> LlmProperties.normalize(client.provider()), Function.identity(),
                            (first, second) -> {
                                throw new IllegalStateException(
                                        "Duplicate LlmClient provider: " + first.provider());
                            }),
                    Map::copyOf));
        this.llmProperties = llmProperties;
        log.info("LLM providers registered={} active={} enabled={}", delegates.keySet(), llmProperties.provider(),
                llmProperties.enabled());
    }

    @Override
    public String provider() {
        return llmProperties.provider();
    }

    @Override
    public LlmResult ask(String promptName, Map<String, Object> vars) {
        if (!llmProperties.enabled()) {
            throw new LlmDisabledException();
        }
        return delegate().ask(promptName, vars);
    }

    private LlmClient delegate() {
        LlmClient delegate = delegates.get(LlmProperties.normalize(llmProperties.provider()));
        if (delegate == null) {
            throw new UnknownLlmProviderException(llmProperties.provider(), delegates.keySet());
        }
        return delegate;
    }

}
