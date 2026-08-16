package com.furkan.apidebugagent.analysis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisStoreTest {

    private static final Instant FROM = Instant.parse("2026-08-06T10:00:00Z");
    private static final Instant TO = Instant.parse("2026-08-06T10:15:00Z");

    private final AnalysisStore store = new AnalysisStore();

    @Test
    void shouldReplayWhatALateSubscriberMissedBeforeLiveEvents() {
        AnalysisRecord record = store.create(FROM, TO);
        store.publish(record.analysisId(), AnalysisEvent.stageStarted(Stage.LOGS));
        store.publish(record.analysisId(), AnalysisEvent.stageFinished(Stage.LOGS, 12, Map.of("logLines", 2)));

        List<AnalysisEvent> received = new ArrayList<>();
        record.subscribe(received::add);
        store.publish(record.analysisId(), AnalysisEvent.stageStarted(Stage.PARSING));

        assertThat(received).extracting(AnalysisEvent::stage)
            .containsExactly(Stage.LOGS, Stage.LOGS, Stage.PARSING);
    }

    @Test
    void shouldDeliverEachEventOnceToASubscriberThatWasAlreadyAttached() {
        AnalysisRecord record = store.create(FROM, TO);
        List<AnalysisEvent> received = new ArrayList<>();
        record.subscribe(received::add);

        store.publish(record.analysisId(), AnalysisEvent.stageStarted(Stage.LOGS));

        assertThat(received).hasSize(1);
    }

    @Test
    void shouldKeepFeedingOtherSubscribersWhenOneOfThemFails() {
        AnalysisRecord record = store.create(FROM, TO);
        List<AnalysisEvent> received = new ArrayList<>();
        record.subscribe(event -> {
            throw new IllegalStateException("client is gone");
        });
        record.subscribe(received::add);

        store.publish(record.analysisId(), AnalysisEvent.stageStarted(Stage.LOGS));

        assertThat(received).hasSize(1);
    }

    @Test
    void shouldStopFeedingASubscriberThatUnsubscribed() {
        AnalysisRecord record = store.create(FROM, TO);
        List<AnalysisEvent> received = new ArrayList<>();
        Runnable unsubscribe = record.subscribe(received::add);

        unsubscribe.run();
        store.publish(record.analysisId(), AnalysisEvent.stageStarted(Stage.LOGS));

        assertThat(received).isEmpty();
    }

    /** What an SSE client does on the terminal event: closes its stream from inside delivery. */
    @Test
    void shouldSurviveASubscriberThatDetachesWhileBeingDeliveredTo() {
        AnalysisRecord record = store.create(FROM, TO);
        List<AnalysisEvent> leaving = new ArrayList<>();
        List<AnalysisEvent> staying = new ArrayList<>();
        Runnable[] unsubscribe = new Runnable[1];
        unsubscribe[0] = record.subscribe(event -> {
            leaving.add(event);
            unsubscribe[0].run();
        });
        record.subscribe(staying::add);

        store.publish(record.analysisId(), AnalysisEvent.stageStarted(Stage.LOGS));
        store.publish(record.analysisId(), AnalysisEvent.stageStarted(Stage.PARSING));

        assertThat(leaving).hasSize(1);
        assertThat(staying).hasSize(2);
    }

    @Test
    void shouldReportRunningUntilTheAnalysisIsComplete() {
        AnalysisRecord record = store.create(FROM, TO);

        assertThat(record.status()).isEqualTo(AnalysisStatus.RUNNING);
        assertThat(record.report()).isEmpty();

        store.complete(record.analysisId(), report(record.analysisId(), AnalysisStatus.COMPLETED));

        assertThat(record.status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(record.report()).isPresent();
    }

    @Test
    void shouldReplayOnlyTheStoredEventsToASubscriberOfAFinishedAnalysis() {
        AnalysisRecord record = store.create(FROM, TO);
        AnalysisReport report = report(record.analysisId(), AnalysisStatus.COMPLETED);
        store.publish(record.analysisId(), AnalysisEvent.stageStarted(Stage.LOGS));
        store.publish(record.analysisId(), AnalysisEvent.report(report));
        store.complete(record.analysisId(), report);

        List<AnalysisEvent> received = new ArrayList<>();
        record.subscribe(received::add);

        assertThat(received).extracting(AnalysisEvent::type)
            .containsExactly(EventType.STAGE_STARTED, EventType.REPORT);
    }

    @Test
    void shouldKeepOnlyTheTenMostRecentAnalyses() {
        List<AnalysisRecord> records = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            records.add(store.create(FROM, TO));
        }

        assertThat(store.find(records.get(0).analysisId())).isEmpty();
        for (int i = 1; i < 11; i++) {
            assertThat(store.find(records.get(i).analysisId())).isPresent();
        }
    }

    @Test
    void shouldIgnoreEventsAndReportsOfAnUnknownAnalysis() {
        store.publish("gone", AnalysisEvent.stageStarted(Stage.LOGS));
        store.complete("gone", report("gone", AnalysisStatus.COMPLETED));

        assertThat(store.find("gone")).isEmpty();
    }

    private AnalysisReport report(String analysisId, AnalysisStatus status) {
        return new AnalysisReport(analysisId, status, FROM, TO, FROM, 42, new AnalysisCounts(0, 0, 0, 0), false,
            List.of(), null);
    }

}
