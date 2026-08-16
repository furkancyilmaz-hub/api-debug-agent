package com.furkan.apidebugagent.sqllog;

import java.time.Instant;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class LogClientTest {

    private static final String BASE_URL = "http://demo-api.test";

    @Test
    void shouldFetchLogsAndAssignSeqByResponseOrder() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String json = twoLines();
        server.expect(MockRestRequestMatchers.requestTo(Matchers.allOf(
                Matchers.startsWith(BASE_URL + "/internal/logs?"),
                Matchers.containsString("from=2024-01-01T10:00:00Z"),
                Matchers.containsString("to=2024-01-01T11:00:00Z"),
                Matchers.containsString("limit=100"))))
            .andRespond(MockRestResponseCreators.withSuccess(json, MediaType.APPLICATION_JSON));

        LogClient logClient = new LogClient(new DemoApiClient(builder, new TargetProperties(BASE_URL)));

        LogWindow result = logClient.fetchLogs(Instant.parse("2024-01-01T10:00:00Z"),
            Instant.parse("2024-01-01T11:00:00Z"), 100);

        List<LogLine> lines = result.lines();
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).seq()).isZero();
        assertThat(lines.get(0).correlationId()).isEqualTo("c1");
        assertThat(lines.get(0).thread()).isEqualTo("http-nio-1");
        assertThat(lines.get(0).logger()).isEqualTo("org.hibernate.SQL");
        assertThat(lines.get(1).seq()).isEqualTo(1);
        assertThat(lines.get(1).logger()).isEqualTo("org.hibernate.orm.jdbc.bind");
        assertThat(result.truncated()).isFalse();
        assertThat(result.limit()).isEqualTo(100);
        server.verify();
    }

    @Test
    void shouldClampLimitAbove5000() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(MockRestRequestMatchers.requestTo(Matchers.allOf(
                Matchers.startsWith(BASE_URL + "/internal/logs?"),
                Matchers.containsString("limit=5000"))))
            .andRespond(MockRestResponseCreators.withSuccess("[]", MediaType.APPLICATION_JSON));

        LogClient logClient = new LogClient(new DemoApiClient(builder, new TargetProperties(BASE_URL)));

        LogWindow result = logClient.fetchLogs(Instant.parse("2024-01-01T10:00:00Z"),
            Instant.parse("2024-01-01T11:00:00Z"), 10_000);

        assertThat(result.lines()).isEmpty();
        assertThat(result.truncated()).isFalse();
        assertThat(result.limit()).isEqualTo(5000);
        server.verify();
    }

    @Test
    void shouldMarkTheWindowTruncatedWhenTheTargetFillsIt() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(MockRestRequestMatchers.requestTo(Matchers.containsString("limit=2")))
            .andRespond(MockRestResponseCreators.withSuccess(twoLines(), MediaType.APPLICATION_JSON));

        LogClient logClient = new LogClient(new DemoApiClient(builder, new TargetProperties(BASE_URL)));

        LogWindow result = logClient.fetchLogs(Instant.parse("2024-01-01T10:00:00Z"),
            Instant.parse("2024-01-01T11:00:00Z"), 2);

        assertThat(result.lines()).hasSize(2);
        assertThat(result.truncated()).isTrue();
        server.verify();
    }

    @Test
    void shouldNotMarkTheWindowTruncatedWhenItComesBackShortOfTheLimit() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(MockRestRequestMatchers.requestTo(Matchers.containsString("limit=3")))
            .andRespond(MockRestResponseCreators.withSuccess(twoLines(), MediaType.APPLICATION_JSON));

        LogClient logClient = new LogClient(new DemoApiClient(builder, new TargetProperties(BASE_URL)));

        LogWindow result = logClient.fetchLogs(Instant.parse("2024-01-01T10:00:00Z"),
            Instant.parse("2024-01-01T11:00:00Z"), 3);

        assertThat(result.lines()).hasSize(2);
        assertThat(result.truncated()).isFalse();
        server.verify();
    }

    @Test
    void shouldFetchRequestsWithMaxLimitAndMapFields() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String json = """
            [
              {"correlationId":"c1","method":"GET","path":"/customers","status":200,"durationMs":842,"timestamp":"2024-01-01T10:00:00Z"}
            ]
            """;
        server.expect(MockRestRequestMatchers.requestTo(Matchers.allOf(
                Matchers.startsWith(BASE_URL + "/internal/requests?"),
                Matchers.containsString("limit=5000"))))
            .andRespond(MockRestResponseCreators.withSuccess(json, MediaType.APPLICATION_JSON));

        LogClient logClient = new LogClient(new DemoApiClient(builder, new TargetProperties(BASE_URL)));

        List<RequestInfo> result = logClient.fetchRequests(Instant.parse("2024-01-01T10:00:00Z"),
            Instant.parse("2024-01-01T11:00:00Z"));

        assertThat(result).containsExactly(
            new RequestInfo("c1", "GET", "/customers", 200, 842, Instant.parse("2024-01-01T10:00:00Z")));
        server.verify();
    }

    /** One SQL line and its bind line — the smallest response the assembler would accept. */
    private static String twoLines() {
        return """
            [
              {"correlationId":"c1","timestamp":"2024-01-01T10:00:00.100Z","level":"DEBUG","logger":"org.hibernate.SQL","thread":"http-nio-1","message":"select p1_0.id from payment p1_0 where p1_0.customer_id=?"},
              {"correlationId":"c1","timestamp":"2024-01-01T10:00:00.100Z","level":"TRACE","logger":"org.hibernate.orm.jdbc.bind","thread":"http-nio-1","message":"binding parameter (1:BIGINT) <- [42]"}
            ]
            """;
    }

}
