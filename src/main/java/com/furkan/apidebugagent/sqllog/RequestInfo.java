package com.furkan.apidebugagent.sqllog;

import java.time.Instant;

public record RequestInfo(String correlationId, String method, String path, int status, long durationMs,
                           Instant timestamp) {
}