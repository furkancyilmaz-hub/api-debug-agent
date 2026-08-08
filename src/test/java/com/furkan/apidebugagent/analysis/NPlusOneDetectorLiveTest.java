package com.furkan.apidebugagent.analysis;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;

import com.furkan.apidebugagent.sqllog.DemoApiClient;
import com.furkan.apidebugagent.sqllog.ExecutedQuery;
import com.furkan.apidebugagent.sqllog.LogClient;
import com.furkan.apidebugagent.sqllog.LogLine;
import com.furkan.apidebugagent.sqllog.RequestInfo;
import com.furkan.apidebugagent.sqllog.SqlNormalizer;
import com.furkan.apidebugagent.sqllog.StatementAssembler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end check against a running demo-api: hits the N+1 endpoint and the join-fetch endpoint,
 * then runs the real log → assemble → detect chain over what those requests actually logged.
 *
 * <p>Requires demo-api on {@code target.base-url}; enable with {@code DEMO_API_LIVE=true}.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DEMO_API_LIVE", matches = ".+")
class NPlusOneDetectorLiveTest {

    private static final String N_PLUS_ONE_PATH = "/api/customers/detail?size=20";

    private static final String JOIN_FETCH_PATH = "/api/customers/overview?size=20";

    @Autowired
    private DemoApiClient demoApiClient;

    @Autowired
    private LogClient logClient;

    @Autowired
    private StatementAssembler statementAssembler;

    @Autowired
    private NPlusOneDetector detector;

    @Test
    void shouldReportFindingWhoseRepeatCountMatchesTheLoggedQueryCount() {
        Captured captured = runAndDetect(N_PLUS_ONE_PATH);

        assertThat(captured.findings()).isNotEmpty();
        Finding finding = captured.findings().get(0);
        assertThat(finding.parentTable()).isEqualTo("customer");
        assertThat(finding.foreignKey()).contains("-> customer.id");
        assertThat(finding.parentSeq()).isLessThan(finding.firstChildSeq());

        // The claim has to hold against the raw log, not just the detector's own bookkeeping.
        assertThat(finding.repeatCount()).isEqualTo(countLoggedExecutions(captured, finding));
    }

    @Test
    void shouldNotReportFindingForJoinFetchEndpoint() {
        assertThat(runAndDetect(JOIN_FETCH_PATH).findings()).isEmpty();
    }

    private Captured runAndDetect(String path) {
        Instant from = Instant.now().minus(5, ChronoUnit.SECONDS);
        demoApiClient.get(path, new ParameterizedTypeReference<Object>() {
        });
        Instant to = Instant.now().plus(5, ChronoUnit.SECONDS);

        List<LogLine> lines = logClient.fetchLogs(from, to, 5000);
        List<RequestInfo> requests = logClient.fetchRequests(from, to);
        List<ExecutedQuery> queries = statementAssembler.assemble(lines);

        // The time window is wide enough to catch the sibling test's request too, so keep only
        // the findings belonging to the endpoint this run actually called.
        String endpoint = "GET " + path.substring(0, path.indexOf('?'));
        List<Finding> findings = detector.detect(queries, requests).stream()
                .filter(finding -> endpoint.equals(finding.endpoint()))
                .toList();
        return new Captured(queries, findings);
    }

    private long countLoggedExecutions(Captured captured, Finding finding) {
        return captured.queries().stream()
                .filter(query -> query.correlationId().equals(finding.correlationId()))
                .filter(query -> SqlNormalizer.normalize(query.rawSql()).equals(finding.normalizedQuery()))
                .count();
    }

    private record Captured(List<ExecutedQuery> queries, List<Finding> findings) {
    }
}
