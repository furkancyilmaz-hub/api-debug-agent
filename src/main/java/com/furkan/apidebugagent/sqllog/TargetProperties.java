package com.furkan.apidebugagent.sqllog;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "target")
public record TargetProperties(String baseUrl) {
}