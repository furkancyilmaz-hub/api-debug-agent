package com.furkan.apidebugagent.sqllog;

import java.time.Instant;

public record LogLine(long seq, String correlationId, Instant timestamp, String thread, String logger,
                       String message) {
}