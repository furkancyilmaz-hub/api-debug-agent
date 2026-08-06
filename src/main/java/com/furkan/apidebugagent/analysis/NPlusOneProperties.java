package com.furkan.apidebugagent.analysis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "analysis.n-plus-one")
public record NPlusOneProperties(@DefaultValue("5") int minRepeat) {
}
