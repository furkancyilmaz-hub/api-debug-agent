package com.furkan.apidebugagent.analysis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.furkan.apidebugagent.llm.FindingEnricher;
import com.furkan.apidebugagent.llm.LlmProperties;
import com.furkan.apidebugagent.sqllog.DemoApiUnavailableException;
import com.furkan.apidebugagent.sqllog.ExecutedQuery;
import com.furkan.apidebugagent.sqllog.LogClient;
import com.furkan.apidebugagent.sqllog.LogLine;
import com.furkan.apidebugagent.sqllog.LogWindow;
import com.furkan.apidebugagent.sqllog.RequestInfo;
import com.furkan.apidebugagent.sqllog.StatementAssembler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceTest {

    private static final Map<String, LlmProperties.ProviderOptions> LLM_PROVIDERS = Map.of("anthropic",
            new LlmProperties.ProviderOptions("claude-sonnet-5", 3000));

    private static final LlmProperties LLM_ENABLED = new LlmProperties(true, "anthropic", LLM_PROVIDERS);
    private static final LlmProperties LLM_DISABLED = new LlmProperties(false, "anthropic", LLM_PROVIDERS);

    private static final AnalysisProperties PROPERTIES = new AnalysisProperties(1000);

    private static final Instant FROM = Instant.parse("2026-08-06T10:00:00Z");
    private static final Instant TO = Instant.parse("2026-08-06T10:15:00Z");

    @Mock
    private LogClient logClient;

    @Mock
    private StatementAssembler statementAssembler;

    @Mock
    private NPlusOneDetector detector;

    @Mock
    private FindingEnricher findingEnricher;

    private final List<AnalysisEvent> events = new ArrayList<>();

    @Test
    void shouldStreamFourStagesAndThenTheReportWhenThereIsSomethingToEnrich() {
        Finding finding = finding("c1", 214);
        pipelineFinds(List.of(finding));
        when(findingEnricher.enrich(List.of(finding))).thenReturn(List.of(explained(finding)));

        AnalysisReport report = analyze(LLM_ENABLED);

        assertThat(stages()).containsExactly(Stage.LOGS, Stage.LOGS, Stage.PARSING, Stage.PARSING, Stage.DETECTION,
            Stage.DETECTION, Stage.ENRICHMENT, Stage.ENRICHMENT);
        assertThat(types()).containsExactly(EventType.STAGE_STARTED, EventType.STAGE_FINISHED,
            EventType.STAGE_STARTED, EventType.STAGE_FINISHED, EventType.STAGE_STARTED, EventType.STAGE_FINISHED,
            EventType.STAGE_STARTED, EventType.STAGE_FINISHED, EventType.REPORT);
        assertThat(last().payload()).isSameAs(report);
        assertThat(report.status()).isEqualTo(AnalysisStatus.COMPLETED);
    }

    @Test
    void shouldStreamThreeStagesAndACompleteReportWhenThereIsNoFinding() {
        pipelineFinds(List.of());

        AnalysisReport report = analyze(LLM_ENABLED);

        assertThat(stages()).doesNotContain(Stage.ENRICHMENT);
        assertThat(last().type()).isEqualTo(EventType.REPORT);
        assertThat(report.status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(report.findings()).isEmpty();
        assertThat(report.counts()).isEqualTo(new AnalysisCounts(2, 1, 1, 0));
        verifyNoInteractions(findingEnricher);
    }

    @Test
    void shouldStreamThreeStagesWhenTheModelLayerIsDisabled() {
        Finding finding = finding("c1", 214);
        pipelineFinds(List.of(finding));

        AnalysisReport report = analyze(LLM_DISABLED);

        assertThat(stages()).doesNotContain(Stage.ENRICHMENT);
        assertThat(report.status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(report.findings()).containsExactly(finding);
        verifyNoInteractions(findingEnricher);
    }

    @Test
    void shouldCallTheModelExactlyOnce() {
        Finding finding = finding("c1", 214);
        pipelineFinds(List.of(finding));
        when(findingEnricher.enrich(any())).thenReturn(List.of(finding));

        analyze(LLM_ENABLED);

        verify(findingEnricher, times(1)).enrich(any());
    }

    @Test
    void shouldMarkOnlyEnrichmentAsModelWork() {
        Finding finding = finding("c1", 214);
        pipelineFinds(List.of(finding));
        when(findingEnricher.enrich(any())).thenReturn(List.of(finding));

        analyze(LLM_ENABLED);

        assertThat(events).filteredOn(e -> e.stage() == Stage.ENRICHMENT)
            .isNotEmpty()
            .allMatch(e -> e.kind() == StageKind.MODEL);
        assertThat(events).filteredOn(e -> e.stage() != null && e.stage() != Stage.ENRICHMENT)
            .allMatch(e -> e.kind() == StageKind.LOCAL);
    }

    @Test
    void shouldCarrySummaryNumbersAndDurationOnFinishedStages() {
        pipelineFinds(List.of(finding("c1", 214)));
        when(findingEnricher.enrich(any())).thenReturn(List.of(finding("c1", 214)));

        analyze(LLM_ENABLED);

        assertThat(finished(Stage.LOGS).payload())
            .isEqualTo(Map.of("logLines", 2, "requests", 1, "truncated", false));
        assertThat(finished(Stage.PARSING).payload()).isEqualTo(Map.of("queries", 1, "correlationIds", 1));
        assertThat(finished(Stage.DETECTION).payload()).isEqualTo(Map.of("findings", 1));
        assertThat(finished(Stage.ENRICHMENT).payload()).isEqualTo(Map.of("enriched", 0));
        assertThat(events).filteredOn(e -> e.type() == EventType.STAGE_FINISHED)
            .allMatch(e -> e.durationMs() != null && e.durationMs() >= 0);
    }

    @Test
    void shouldCountEnrichedFindings() {
        Finding finding = finding("c1", 214);
        pipelineFinds(List.of(finding));
        when(findingEnricher.enrich(any())).thenReturn(List.of(explained(finding)));

        analyze(LLM_ENABLED);

        assertThat(finished(Stage.ENRICHMENT).payload()).isEqualTo(Map.of("enriched", 1));
    }

    @Test
    void shouldStreamAnErrorEventAndReturnAFailedReportWhenTheTargetIsUnreachable() {
        when(logClient.fetchLogs(any(), any(), anyInt()))
            .thenThrow(new DemoApiUnavailableException("connection refused", new RuntimeException()));

        AnalysisReport report = analyze(LLM_ENABLED);

        assertThat(types()).containsExactly(EventType.STAGE_STARTED, EventType.ERROR);
        AnalysisEvent error = last();
        assertThat(error.stage()).isEqualTo(Stage.LOGS);
        assertThat(error.payload()).isEqualTo(Map.of("message", "Hedef servise ulaşılamıyor."));
        assertThat(report.status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(report.error()).isEqualTo("Hedef servise ulaşılamıyor.");
        assertThat(report.findings()).isEmpty();
        verifyNoInteractions(statementAssembler, detector, findingEnricher);
    }

    @Test
    void shouldReportTheStageAnalysisBrokeOffIn() {
        when(logClient.fetchLogs(any(), any(), anyInt())).thenReturn(logWindow(true));
        when(logClient.fetchRequests(any(), any())).thenReturn(List.of(request()));
        when(statementAssembler.assemble(any())).thenThrow(new IllegalStateException("bind line parser exploded"));

        AnalysisReport report = analyze(LLM_ENABLED);

        assertThat(last().stage()).isEqualTo(Stage.PARSING);
        assertThat(last().payload()).isEqualTo(Map.of("message", "Analiz sırasında beklenmeyen bir hata oluştu."));
        assertThat(report.counts()).isEqualTo(new AnalysisCounts(2, 1, 0, 0));
        // The window was already known to be short when parsing blew up; a failed report says so too.
        assertThat(report.logsTruncated()).isTrue();
    }

    @Test
    void shouldCarryAFullLogWindowIntoTheStreamAndTheReport() {
        when(logClient.fetchLogs(any(), any(), anyInt())).thenReturn(logWindow(true));
        when(logClient.fetchRequests(any(), any())).thenReturn(List.of(request()));
        when(statementAssembler.assemble(any())).thenReturn(List.of(query()));
        when(detector.detect(any(), any())).thenReturn(List.of(finding("c1", 92)));

        AnalysisReport report = analyze(LLM_DISABLED);

        assertThat(finished(Stage.LOGS).payload())
            .isEqualTo(Map.of("logLines", 2, "requests", 1, "truncated", true));
        assertThat(report.logsTruncated()).isTrue();
        assertThat(report.status()).isEqualTo(AnalysisStatus.COMPLETED);
    }

    @Test
    void shouldNotClaimTruncationWhenTheWindowHadRoomLeft() {
        pipelineFinds(List.of());

        AnalysisReport report = analyze(LLM_DISABLED);

        assertThat(report.logsTruncated()).isFalse();
    }

    private AnalysisReport analyze(LlmProperties llmProperties) {
        AnalysisService service = new AnalysisService(logClient, statementAssembler, detector, findingEnricher,
            llmProperties, PROPERTIES);
        return service.analyze("a1", FROM, TO, events::add);
    }

    /** Two log lines, one request, one query, and whatever detection is told to find. */
    private void pipelineFinds(List<Finding> findings) {
        when(logClient.fetchLogs(any(), any(), anyInt())).thenReturn(logWindow(false));
        when(logClient.fetchRequests(any(), any())).thenReturn(List.of(request()));
        when(statementAssembler.assemble(any())).thenReturn(List.of(query()));
        when(detector.detect(any(), any())).thenReturn(findings);
    }

    private LogWindow logWindow(boolean truncated) {
        List<LogLine> lines = List.of(
            new LogLine(0, "c1", FROM, "http-1", "org.hibernate.SQL", "select 1"),
            new LogLine(1, "c1", FROM, "http-1", "org.hibernate.orm.jdbc.bind", "binding parameter (1:BIGINT) <- [42]"));
        return new LogWindow(lines, truncated, truncated ? lines.size() : PROPERTIES.logLimit());
    }

    private RequestInfo request() {
        return new RequestInfo("c1", "GET", "/customers", 200, 1180, FROM);
    }

    private ExecutedQuery query() {
        return new ExecutedQuery("c1", 0, FROM, "select p1_0.id from payment p1_0 where p1_0.customer_id=?", List.of());
    }

    private Finding finding(String correlationId, int repeatCount) {
        return new Finding(correlationId, "GET /customers", "customer", "payment",
            "payment.customer_id -> customer.id", "select p1_0.id from payment p1_0 where p1_0.customer_id = ?",
            repeatCount, repeatCount, Confidence.HIGH, List.of("42"), 10L, 11L);
    }

    private Finding explained(Finding finding) {
        return finding.withEnrichment("Her müşterinin ödemeleri ayrı sorguyla çekiliyor.", null);
    }

    private List<Stage> stages() {
        return events.stream().map(AnalysisEvent::stage).filter(stage -> stage != null).toList();
    }

    private List<EventType> types() {
        return events.stream().map(AnalysisEvent::type).toList();
    }

    private AnalysisEvent finished(Stage stage) {
        return events.stream()
            .filter(e -> e.type() == EventType.STAGE_FINISHED && e.stage() == stage)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No STAGE_FINISHED event for stage " + stage));
    }

    private AnalysisEvent last() {
        return events.get(events.size() - 1);
    }

}
