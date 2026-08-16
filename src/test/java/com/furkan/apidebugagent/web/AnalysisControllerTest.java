package com.furkan.apidebugagent.web;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.furkan.apidebugagent.analysis.AnalysisCounts;
import com.furkan.apidebugagent.analysis.AnalysisEvent;
import com.furkan.apidebugagent.analysis.AnalysisLauncher;
import com.furkan.apidebugagent.analysis.AnalysisRecord;
import com.furkan.apidebugagent.analysis.AnalysisReport;
import com.furkan.apidebugagent.analysis.AnalysisStatus;
import com.furkan.apidebugagent.analysis.AnalysisStore;
import com.furkan.apidebugagent.analysis.Stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@WebMvcTest(AnalysisController.class)
@EnableConfigurationProperties(CorsProperties.class)
class AnalysisControllerTest {

    private static final Instant FROM = Instant.parse("2026-08-06T10:00:00Z");
    private static final Instant TO = Instant.parse("2026-08-06T10:15:00Z");

    private static final String VALID_BODY = """
        { "from": "2026-08-06T10:00:00Z", "to": "2026-08-06T10:15:00Z" }
        """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalysisLauncher analysisLauncher;

    @MockitoBean
    private AnalysisStore analysisStore;

    /** A real store, used only to build the records the mocked one hands out. */
    private final AnalysisStore records = new AnalysisStore();

    @Test
    void shouldStartAnAnalysisAndReturnItsId() throws Exception {
        when(analysisLauncher.start(FROM, TO)).thenReturn("a1");

        MvcResult result = analyze(VALID_BODY);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).contains("\"analysisId\":\"a1\"");
    }

    @Test
    void shouldRejectARangeThatDoesNotMoveForward() throws Exception {
        MvcResult result = analyze("""
            { "from": "2026-08-06T10:15:00Z", "to": "2026-08-06T10:00:00Z" }
            """);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        verifyNoInteractions(analysisLauncher);
    }

    @Test
    void shouldRejectARequestWithoutBothEndsOfTheRange() throws Exception {
        MvcResult result = analyze("{ \"from\": \"2026-08-06T10:00:00Z\" }");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        verifyNoInteractions(analysisLauncher);
    }

    @Test
    void shouldReturnTheReportOfAFinishedAnalysis() throws Exception {
        AnalysisRecord record = record();
        records.complete(record.analysisId(), report(record.analysisId()));
        when(analysisStore.find(record.analysisId())).thenReturn(Optional.of(record));

        MvcResult result = mockMvc.perform(get("/api/analyze/{id}", record.analysisId())).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).contains("\"status\":\"COMPLETED\"");
    }

    @Test
    void shouldAnswerAcceptedWhileTheAnalysisIsStillRunning() throws Exception {
        AnalysisRecord record = record();
        when(analysisStore.find(record.analysisId())).thenReturn(Optional.of(record));

        MvcResult result = mockMvc.perform(get("/api/analyze/{id}", record.analysisId())).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(202);
        assertThat(result.getResponse().getContentAsString()).isEmpty();
    }

    @Test
    void shouldAnswerNotFoundForAnUnknownAnalysis() throws Exception {
        when(analysisStore.find(any())).thenReturn(Optional.empty());

        assertThat(mockMvc.perform(get("/api/analyze/{id}", "gone")).andReturn().getResponse().getStatus())
            .isEqualTo(404);
        assertThat(mockMvc.perform(get("/api/analyze/{id}/stream", "gone")).andReturn().getResponse().getStatus())
            .isEqualTo(404);
    }

    @Test
    void shouldReplayTheStoredEventsToAClientThatConnectsAfterTheAnalysis() throws Exception {
        AnalysisRecord record = record();
        AnalysisReport report = report(record.analysisId());
        records.publish(record.analysisId(), AnalysisEvent.stageStarted(Stage.LOGS));
        records.publish(record.analysisId(), AnalysisEvent.report(report));
        records.complete(record.analysisId(), report);
        when(analysisStore.find(record.analysisId())).thenReturn(Optional.of(record));

        MvcResult result = mockMvc.perform(get("/api/analyze/{id}/stream", record.analysisId())).andReturn();

        assertThat(result.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("event:STAGE_STARTED", "\"stage\":\"loglar\"", "event:REPORT");
    }

    private MvcResult analyze(String body) throws Exception {
        return mockMvc.perform(post("/api/analyze").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private AnalysisRecord record() {
        return records.create(FROM, TO);
    }

    private AnalysisReport report(String analysisId) {
        return new AnalysisReport(analysisId, AnalysisStatus.COMPLETED, FROM, TO, FROM, 42,
            new AnalysisCounts(2, 1, 1, 0), false, List.of(), null);
    }

}
