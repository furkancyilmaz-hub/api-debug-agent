package com.furkan.apidebugagent.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "llm")
public record LlmProperties(@DefaultValue("true") boolean enabled, String model, int maxTokens) {
}