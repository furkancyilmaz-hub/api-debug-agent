package com.furkan.apidebugagent.llm;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "llm")
public record LlmProperties(@DefaultValue("true") boolean enabled, @DefaultValue("anthropic") String provider,
        Map<String, ProviderOptions> providers) {

    public LlmProperties {
        providers = providers == null ? Map.of()
                : providers.entrySet()
                    .stream()
                    .collect(Collectors.toUnmodifiableMap(entry -> normalize(entry.getKey()), Map.Entry::getValue));
    }

    public ProviderOptions optionsFor(String providerName) {
        String key = normalize(providerName);
        ProviderOptions options = providers.get(key);
        if (options == null) {
            throw new LlmProviderUnavailableException(providerName, "llm.providers." + key + " is not configured");
        }
        if (options.model() == null || options.model().isBlank()) {
            throw new LlmProviderUnavailableException(providerName, "llm.providers." + key + ".model is not set");
        }
        return options;
    }

    static String normalize(String providerName) {
        return providerName.toLowerCase(Locale.ROOT);
    }

    public record ProviderOptions(String model, @DefaultValue("3000") int maxTokens) {
    }

}
