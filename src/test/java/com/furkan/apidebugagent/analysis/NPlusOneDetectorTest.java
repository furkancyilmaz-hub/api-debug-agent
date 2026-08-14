package com.furkan.apidebugagent.analysis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntUnaryOperator;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.furkan.apidebugagent.schema.ForeignKey;
import com.furkan.apidebugagent.schema.ForeignKeyCache;
import com.furkan.apidebugagent.sqllog.BindParameter;
import com.furkan.apidebugagent.sqllog.ExecutedQuery;
import com.furkan.apidebugagent.sqllog.RequestInfo;
import com.furkan.apidebugagent.sqllog.SqlNormalizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class NPlusOneDetectorTest {

    private static final String CID = "req-1";

    private static final String PARENT_SQL = "select c1_0.id,c1_0.name from customer c1_0";

    private static final String PAYMENT_CHILD_SQL =
            "select p1_0.id,p1_0.amount from payment p1_0 where p1_0.customer_id=?";

    private static final String ADDRESS_CHILD_SQL =
            "select a1_0.id,a1_0.city from address a1_0 where a1_0.customer_id=?";

    private static final ForeignKey PAYMENT_FK = new ForeignKey("payment", "customer_id", "customer", "id");

    private static final ForeignKey ADDRESS_FK = new ForeignKey("address", "customer_id", "customer", "id");

    private static final Map<String, ForeignKey> FOREIGN_KEYS = Map.of(
            "payment.customer_id", PAYMENT_FK,
            "address.customer_id", ADDRESS_FK);

    @Mock
    private ForeignKeyCache foreignKeyCache;

    private NPlusOneDetector detector;

    @BeforeEach
    void setUp() {
        lenient().when(foreignKeyCache.byChildColumn(anyString(), anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(
                        FOREIGN_KEYS.get(invocation.getArgument(0) + "." + invocation.getArgument(1))));
        detector = new NPlusOneDetector(foreignKeyCache, new NPlusOneProperties(5));
    }

    @Test
    void shouldReportHighConfidenceFindingForClassicNPlusOne() {
        List<ExecutedQuery> queries = new ArrayList<>(List.of(query(0, PARENT_SQL)));
        queries.addAll(paymentChildren(1, 200, i -> i + 1));

        List<Finding> findings = detector.detect(queries, requests());

        assertThat(findings).hasSize(1);
        Finding finding = findings.get(0);
        assertThat(finding.correlationId()).isEqualTo(CID);
        assertThat(finding.endpoint()).isEqualTo("GET /customers");
        assertThat(finding.parentTable()).isEqualTo("customer");
        assertThat(finding.childTable()).isEqualTo("payment");
        assertThat(finding.foreignKey()).isEqualTo("payment.customer_id -> customer.id");
        assertThat(finding.normalizedQuery()).isEqualTo(SqlNormalizer.normalize(PAYMENT_CHILD_SQL));
        assertThat(finding.repeatCount()).isEqualTo(200);
        assertThat(finding.distinctBindCount()).isEqualTo(200);
        assertThat(finding.confidence()).isEqualTo(Confidence.HIGH);
        // Every distinct bind reaches the report — the evidence is never sampled down.
        assertThat(finding.bindValues()).containsExactlyElementsOf(bindValues(1, 200));
        assertThat(finding.parentSeq()).isZero();
        assertThat(finding.firstChildSeq()).isEqualTo(1);
    }

    @Test
    void shouldNotReportFindingWhenSingleJoinFetchQueryIsUsed() {
        List<ExecutedQuery> queries = List.of(query(0,
                "select c1_0.id,p1_0.amount from customer c1_0 join payment p1_0 on p1_0.customer_id=c1_0.id"));

        assertThat(detector.detect(queries, requests())).isEmpty();
    }

    @Test
    void shouldNotReportFindingWhenRepeatCountIsBelowThreshold() {
        List<ExecutedQuery> queries = new ArrayList<>(List.of(query(0, PARENT_SQL)));
        queries.addAll(paymentChildren(1, 3, i -> i + 1));

        assertThat(detector.detect(queries, requests())).isEmpty();
    }

    @Test
    void shouldLowerConfidenceWhenSameBindValueRepeats() {
        List<ExecutedQuery> queries = new ArrayList<>(List.of(query(0, PARENT_SQL)));
        queries.addAll(paymentChildren(1, 200, i -> 7));

        List<Finding> findings = detector.detect(queries, requests());

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).repeatCount()).isEqualTo(200);
        assertThat(findings.get(0).distinctBindCount()).isEqualTo(1);
        assertThat(findings.get(0).confidence()).isEqualTo(Confidence.MEDIUM);
        assertThat(findings.get(0).bindValues()).containsExactly("7");
    }

    @Test
    void shouldLowerConfidenceWhenBindValuesPartiallyRepeat() {
        List<ExecutedQuery> queries = new ArrayList<>(List.of(query(0, PARENT_SQL)));
        queries.addAll(paymentChildren(1, 200, i -> i % 20));

        List<Finding> findings = detector.detect(queries, requests());

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).distinctBindCount()).isEqualTo(20);
        assertThat(findings.get(0).confidence()).isEqualTo(Confidence.MEDIUM);
    }

    @Test
    void shouldNotReportFindingWhenParentRunsAfterChildren() {
        List<ExecutedQuery> queries = new ArrayList<>(paymentChildren(0, 10, i -> i + 1));
        queries.add(query(10, PARENT_SQL));

        assertThat(detector.detect(queries, requests())).isEmpty();
    }

    @Test
    void shouldNotReportFindingWhenParentQueryIsMissing() {
        assertThat(detector.detect(paymentChildren(0, 10, i -> i + 1), requests())).isEmpty();
    }

    @Test
    void shouldReportTwoFindingsForTwoNPlusOnesInSameRequest() {
        List<ExecutedQuery> queries = new ArrayList<>(List.of(query(0, PARENT_SQL)));
        queries.addAll(paymentChildren(1, 10, i -> i + 1));
        queries.addAll(children(11, 10, ADDRESS_CHILD_SQL, i -> i + 1));

        List<Finding> findings = detector.detect(queries, requests());

        assertThat(findings).hasSize(2);
        assertThat(findings).extracting(Finding::childTable).containsExactly("payment", "address");
        assertThat(findings).extracting(Finding::firstChildSeq).containsExactly(1L, 11L);
        assertThat(findings).allSatisfy(finding -> assertThat(finding.parentSeq()).isZero());
    }

    @Test
    void shouldNotReportFindingWhenRepeatedColumnIsNotAForeignKey() {
        List<ExecutedQuery> queries = new ArrayList<>(List.of(query(0, PARENT_SQL)));
        queries.addAll(children(1, 10, "select p1_0.id from payment p1_0 where p1_0.amount=?", i -> i + 1));

        assertThat(detector.detect(queries, requests())).isEmpty();
    }

    @Test
    void shouldUseForeignKeyBindPositionWhenQueryHasMultipleParameters() {
        String sql = "select p1_0.id from payment p1_0 where p1_0.status=? and p1_0.customer_id=?";
        List<ExecutedQuery> queries = new ArrayList<>(List.of(query(0, PARENT_SQL)));
        for (int i = 0; i < 10; i++) {
            queries.add(query(1 + i, sql, new BindParameter(1, "VARCHAR", "ACTIVE"), bind(2, i + 1)));
        }

        List<Finding> findings = detector.detect(queries, requests());

        assertThat(findings).hasSize(1);
        // Blindly taking the first bind would have produced distinctBindCount == 1 ("ACTIVE").
        assertThat(findings.get(0).distinctBindCount()).isEqualTo(10);
        assertThat(findings.get(0).confidence()).isEqualTo(Confidence.HIGH);
        assertThat(findings.get(0).bindValues()).containsExactlyElementsOf(bindValues(1, 10));
    }

    @Test
    void shouldSkipUnparseableQueriesWithoutBreakingDetection() {
        List<ExecutedQuery> queries = new ArrayList<>(List.of(query(0, PARENT_SQL)));
        queries.addAll(paymentChildren(1, 10, i -> i + 1));
        queries.add(query(11, "update payment set amount=1 where id=?", bind(1, 9)));

        assertThat(detector.detect(queries, requests())).hasSize(1);
    }

    @Test
    void shouldExcludeChildRunsWhoseForeignKeyBindIsMissing() {
        List<ExecutedQuery> queries = new ArrayList<>(List.of(query(0, PARENT_SQL)));
        queries.addAll(paymentChildren(1, 9, i -> i + 1));
        queries.add(query(10, PAYMENT_CHILD_SQL));

        List<Finding> findings = detector.detect(queries, requests());

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).repeatCount()).isEqualTo(9);
        assertThat(findings.get(0).distinctBindCount()).isEqualTo(9);
    }

    @Test
    void shouldReportFindingsPerCorrelationIdSeparately() {
        List<ExecutedQuery> queries = new ArrayList<>();
        queries.add(query("req-1", 0, PARENT_SQL));
        queries.addAll(paymentChildren("req-1", 1, 10, i -> i + 1));
        queries.add(query("req-2", 0, PARENT_SQL));
        queries.addAll(paymentChildren("req-2", 1, 10, i -> i + 1));

        List<Finding> findings = detector.detect(queries, List.of(
                request("req-1", "GET", "/customers"),
                request("req-2", "GET", "/reports")));

        assertThat(findings).hasSize(2);
        assertThat(findings).extracting(Finding::correlationId).containsExactlyInAnyOrder("req-1", "req-2");
        assertThat(findings).extracting(Finding::endpoint)
                .containsExactlyInAnyOrder("GET /customers", "GET /reports");
    }

    @Test
    void shouldReportFindingWithNullEndpointWhenRequestInfoIsMissing() {
        List<ExecutedQuery> queries = new ArrayList<>(List.of(query(0, PARENT_SQL)));
        queries.addAll(paymentChildren(1, 10, i -> i + 1));

        List<Finding> findings = detector.detect(queries, List.of());

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).endpoint()).isNull();
    }

    @Test
    void shouldReadRepeatThresholdFromConfiguration() {
        List<ExecutedQuery> queries = new ArrayList<>(List.of(query(0, PARENT_SQL)));
        queries.addAll(paymentChildren(1, 10, i -> i + 1));

        assertThat(new NPlusOneDetector(foreignKeyCache, new NPlusOneProperties(50)).detect(queries, requests()))
                .isEmpty();
        assertThat(new NPlusOneDetector(foreignKeyCache, new NPlusOneProperties(10)).detect(queries, requests()))
                .hasSize(1);
    }

    @Test
    void shouldReturnEmptyListForNoQueries() {
        assertThat(detector.detect(List.of(), requests())).isEmpty();
    }

    private static List<RequestInfo> requests() {
        return List.of(request(CID, "GET", "/customers"));
    }

    private static RequestInfo request(String correlationId, String method, String path) {
        return new RequestInfo(correlationId, method, path, 200, 120, Instant.EPOCH);
    }

    private static List<ExecutedQuery> paymentChildren(int fromSeq, int count, IntUnaryOperator customerId) {
        return children(fromSeq, count, PAYMENT_CHILD_SQL, customerId);
    }

    private static List<ExecutedQuery> paymentChildren(String correlationId, int fromSeq, int count,
            IntUnaryOperator customerId) {
        List<ExecutedQuery> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(query(correlationId, fromSeq + i, PAYMENT_CHILD_SQL, bind(1, customerId.applyAsInt(i))));
        }
        return out;
    }

    private static List<ExecutedQuery> children(int fromSeq, int count, String sql, IntUnaryOperator bindValue) {
        List<ExecutedQuery> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(query(fromSeq + i, sql, bind(1, bindValue.applyAsInt(i))));
        }
        return out;
    }

    private static ExecutedQuery query(int seq, String sql, BindParameter... binds) {
        return query(CID, seq, sql, binds);
    }

    private static ExecutedQuery query(String correlationId, int seq, String sql, BindParameter... binds) {
        return new ExecutedQuery(correlationId, seq, Instant.EPOCH.plusMillis(seq), sql, List.of(binds));
    }

    private static BindParameter bind(int index, int value) {
        return new BindParameter(index, "BIGINT", String.valueOf(value));
    }

    private static List<String> bindValues(int fromInclusive, int toInclusive) {
        return IntStream.rangeClosed(fromInclusive, toInclusive).mapToObj(String::valueOf).toList();
    }
}
