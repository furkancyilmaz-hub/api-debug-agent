package com.furkan.apidebugagent.sqllog;

import java.time.Instant;
import java.util.List;

public record ExecutedQuery(String correlationId, int seq, Instant timestamp, String rawSql,
                             List<BindParameter> binds) {
}