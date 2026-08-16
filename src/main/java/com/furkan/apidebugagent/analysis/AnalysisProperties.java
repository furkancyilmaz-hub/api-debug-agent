package com.furkan.apidebugagent.analysis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param logLimit how many log lines one analysis asks for; the hard ceiling of 5000 lives in
 *                 {@code LogClient} and matches what the target allows, so the default asks for
 *                 the whole of it — a smaller window fills up on large pages and makes repeat
 *                 counts look smaller than they were
 */
@ConfigurationProperties(prefix = "analysis")
public record AnalysisProperties(@DefaultValue("5000") int logLimit) {
}
