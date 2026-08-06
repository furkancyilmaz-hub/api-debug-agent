package com.furkan.apidebugagent.web;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param allowedOrigins who may call {@code /api/**} from a browser; {@code debug-console} runs on
 *                       5173 in development
 */
@ConfigurationProperties(prefix = "web.cors")
public record CorsProperties(@DefaultValue("http://localhost:5173") List<String> allowedOrigins) {
}
