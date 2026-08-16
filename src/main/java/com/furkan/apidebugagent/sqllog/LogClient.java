package com.furkan.apidebugagent.sqllog;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class LogClient {

    private static final Logger log = LoggerFactory.getLogger(LogClient.class);

    private static final String LOGS_PATH = "/internal/logs";
    private static final String REQUESTS_PATH = "/internal/requests";
    private static final int MAX_LIMIT = 5000;

    private final DemoApiClient demoApiClient;

    public LogClient(DemoApiClient demoApiClient) {
        this.demoApiClient = demoApiClient;
    }

    /**
     * @return the lines, marked as truncated when the target filled the window exactly — the caller
     *         is expected to carry that into the report rather than quietly under-report
     */
    public LogWindow fetchLogs(Instant from, Instant to, int limit) {
        int effectiveLimit = clampLimit(limit, "fetchLogs");
        String uri = UriComponentsBuilder.fromPath(LOGS_PATH)
            .queryParam("from", from)
            .queryParam("to", to)
            .queryParam("limit", effectiveLimit)
            .build()
            .encode()
            .toUriString();

        List<LogEntryPayload> payload = demoApiClient.get(uri, new ParameterizedTypeReference<List<LogEntryPayload>>() {
        });

        List<LogLine> lines = new ArrayList<>(payload.size());
        for (int i = 0; i < payload.size(); i++) {
            LogEntryPayload entry = payload.get(i);
            lines.add(new LogLine(i, entry.correlationId(), entry.timestamp(), entry.thread(), entry.logger(),
                entry.message()));
        }

        boolean truncated = lines.size() >= effectiveLimit;
        if (truncated) {
            log.warn("fetchLogs returned the full window of {} lines; the range holds more and the analysis will "
                + "see only its beginning", effectiveLimit);
        }
        return new LogWindow(lines, truncated, effectiveLimit);
    }

    public List<RequestInfo> fetchRequests(Instant from, Instant to) {
        String uri = UriComponentsBuilder.fromPath(REQUESTS_PATH)
            .queryParam("from", from)
            .queryParam("to", to)
            .queryParam("limit", MAX_LIMIT)
            .build()
            .encode()
            .toUriString();

        List<RequestInfo> requests = demoApiClient.get(uri, new ParameterizedTypeReference<List<RequestInfo>>() {
        });

        if (requests.size() == MAX_LIMIT) {
            log.warn("fetchRequests returned exactly the max limit ({}); results may be truncated", MAX_LIMIT);
        }
        return requests;
    }

    private int clampLimit(int requestedLimit, String caller) {
        if (requestedLimit > MAX_LIMIT) {
            log.warn("{} requested limit {} exceeds max {}, clamping", caller, requestedLimit, MAX_LIMIT);
            return MAX_LIMIT;
        }
        return requestedLimit;
    }

    private record LogEntryPayload(String correlationId, Instant timestamp, LogLevel level, String logger,
                                    String thread, String message) {
    }

}