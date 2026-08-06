package com.furkan.apidebugagent.analysis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param logLimit how many log lines one analysis asks for; the hard ceiling of 5000 lives in
 *                 {@code LogClient}
 */
@ConfigurationProperties(prefix = "analysis")
public record AnalysisProperties(@DefaultValue("1000") int logLimit) {
}
