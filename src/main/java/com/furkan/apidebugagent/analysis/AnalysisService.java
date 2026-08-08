package com.furkan.apidebugagent.analysis;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.furkan.apidebugagent.llm.FindingEnricher;
import com.furkan.apidebugagent.llm.LlmProperties;
import com.furkan.apidebugagent.schema.SchemaUnavailableException;
import com.furkan.apidebugagent.sqllog.DemoApiUnavailableException;
import com.furkan.apidebugagent.sqllog.ExecutedQuery;
import com.furkan.apidebugagent.sqllog.LogClient;
import com.furkan.apidebugagent.sqllog.LogLine;
import com.furkan.apidebugagent.sqllog.RequestInfo;
import com.furkan.apidebugagent.sqllog.StatementAssembler;

/**
 * Runs the four stages of an analysis and announces each of them as it goes.
 *
 * <p>Events leave through a {@code Consumer}, not an emitter: this class does not know that a
 * browser is watching, and a test can collect the whole stream into a list.
 *
 * <p>The report is finished when detection ends. Enrichment is one model call on top of it and is
 * skipped — not failed, skipped — when there is no finding or the model layer is off; the analysis
 * then streams three stages and the report is just as complete.
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final LogClient logClient;

    private final StatementAssembler statementAssembler;

    private final NPlusOneDetector detector;

    private final FindingEnricher findingEnricher;

    private final LlmProperties llmProperties;

    private final AnalysisProperties properties;

    public AnalysisService(LogClient logClient, StatementAssembler statementAssembler, NPlusOneDetector detector,
            FindingEnricher findingEnricher, LlmProperties llmProperties, AnalysisProperties properties) {

        this.logClient = logClient;
        this.statementAssembler = statementAssembler;
        this.detector = detector;
        this.findingEnricher = findingEnricher;
        this.llmProperties = llmProperties;
        this.properties = properties;
    }

    /**
     * @param events receives every stage event and then either the report or the error; a failure
     *               is announced through here rather than thrown
     * @return the finished report, or a {@link AnalysisStatus#FAILED} one carrying the same message
     *         that was streamed
     */
    public AnalysisReport analyze(String analysisId, Instant from, Instant to, Consumer<AnalysisEvent> events) {
        Instant startedAt = Instant.now();
        long analysisStart = System.nanoTime();
        Stage current = Stage.LOGS;
        int logLines = 0;
        int requestCount = 0;
        int queryCount = 0;

        try {
            events.accept(AnalysisEvent.stageStarted(Stage.LOGS));
            long stageStart = System.nanoTime();
            List<LogLine> lines = logClient.fetchLogs(from, to, properties.logLimit());
            List<RequestInfo> requests = logClient.fetchRequests(from, to);
            logLines = lines.size();
            requestCount = requests.size();
            events.accept(AnalysisEvent.stageFinished(Stage.LOGS, millisSince(stageStart),
                Map.of("logLines", logLines, "requests", requestCount)));

            current = Stage.PARSING;
            events.accept(AnalysisEvent.stageStarted(Stage.PARSING));
            stageStart = System.nanoTime();
            List<ExecutedQuery> queries = statementAssembler.assemble(lines);
            queryCount = queries.size();
            events.accept(AnalysisEvent.stageFinished(Stage.PARSING, millisSince(stageStart),
                Map.of("queries", queryCount, "correlationIds", correlationIds(queries))));

            current = Stage.DETECTION;
            events.accept(AnalysisEvent.stageStarted(Stage.DETECTION));
            stageStart = System.nanoTime();
            List<Finding> findings = detector.detect(queries, requests);
            events.accept(AnalysisEvent.stageFinished(Stage.DETECTION, millisSince(stageStart),
                Map.of("findings", findings.size())));

            // Everything above is measured and the report already stands. The model is asked once,
            // only when there is something to explain and only when it is switched on.
            if (llmProperties.enabled() && !findings.isEmpty()) {
                current = Stage.ENRICHMENT;
                events.accept(AnalysisEvent.stageStarted(Stage.ENRICHMENT));
                stageStart = System.nanoTime();
                findings = findingEnricher.enrich(findings);
                events.accept(AnalysisEvent.stageFinished(Stage.ENRICHMENT, millisSince(stageStart),
                    Map.of("enriched", explained(findings))));
            }
            else {
                log.debug("Analysis {} skips enrichment: llmEnabled={}, findings={}", analysisId,
                    llmProperties.enabled(), findings.size());
            }

            AnalysisReport report = new AnalysisReport(analysisId, AnalysisStatus.COMPLETED, from, to, startedAt,
                millisSince(analysisStart), new AnalysisCounts(logLines, requestCount, queryCount, findings.size()),
                findings, null);
            events.accept(AnalysisEvent.report(report));
            return report;
        }
        // The analysis is a background job with a client watching: a failure has to become an event
        // and a stored report, not an exception nobody is left to catch.
        catch (RuntimeException e) {
            log.error("Analysis {} failed in stage {}", analysisId, current.wireName(), e);
            String message = messageFor(e);
            events.accept(AnalysisEvent.error(current, message));
            return new AnalysisReport(analysisId, AnalysisStatus.FAILED, from, to, startedAt,
                millisSince(analysisStart), new AnalysisCounts(logLines, requestCount, queryCount, 0), List.of(),
                message);
        }
    }

    private int correlationIds(List<ExecutedQuery> queries) {
        Set<String> ids = new HashSet<>();
        for (ExecutedQuery query : queries) {
            ids.add(query.correlationId());
        }
        return ids.size();
    }

    /** How many findings came back from the model with prose on them. */
    private int explained(List<Finding> findings) {
        int explained = 0;
        for (Finding finding : findings) {
            if (finding.explanation() != null) {
                explained++;
            }
        }
        return explained;
    }

    /** What the client is told. The exception itself, with its stacktrace, stays in the log. */
    private String messageFor(RuntimeException e) {
        return switch (e) {
            case DemoApiUnavailableException ignored -> "Hedef servise ulaşılamıyor.";
            case SchemaUnavailableException ignored -> "Şema metadata'sı alınamadı.";
            default -> "Analiz sırasında beklenmeyen bir hata oluştu.";
        };
    }

    private static long millisSince(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

}
