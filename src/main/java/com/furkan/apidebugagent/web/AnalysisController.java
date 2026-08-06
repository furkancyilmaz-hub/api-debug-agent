package com.furkan.apidebugagent.web;

import java.io.IOException;
import java.time.Duration;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.furkan.apidebugagent.analysis.AnalysisEvent;
import com.furkan.apidebugagent.analysis.AnalysisLauncher;
import com.furkan.apidebugagent.analysis.AnalysisRecord;
import com.furkan.apidebugagent.analysis.AnalysisReport;
import com.furkan.apidebugagent.analysis.AnalysisStore;

/**
 * The three endpoints of {@code contract.md} §4: start an analysis, watch it, read the report.
 * Watching is optional — the report is there either way.
 */
@RestController
@RequestMapping("/api/analyze")
public class AnalysisController {

    private static final Logger log = LoggerFactory.getLogger(AnalysisController.class);

    /** Generous: an analysis takes tens of seconds and an idle stream costs nothing. */
    private static final long STREAM_TIMEOUT_MS = Duration.ofMinutes(5).toMillis();

    private final AnalysisLauncher analysisLauncher;

    private final AnalysisStore analysisStore;

    public AnalysisController(AnalysisLauncher analysisLauncher, AnalysisStore analysisStore) {
        this.analysisLauncher = analysisLauncher;
        this.analysisStore = analysisStore;
    }

    @PostMapping
    public AnalyzeResponse analyze(@RequestBody AnalyzeRequest request) {
        validate(request);
        return new AnalyzeResponse(analysisLauncher.start(request.from(), request.to()));
    }

    /**
     * The event stream of one analysis. Whoever connects gets the stages that already happened
     * before the live ones, so a late client still sees the whole run.
     */
    @GetMapping(path = "/{analysisId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String analysisId) {
        AnalysisRecord record = record(analysisId);
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);

        Runnable unsubscribe = record.subscribe(new EmitterListener(emitter, analysisId));
        emitter.onCompletion(unsubscribe);
        emitter.onError(e -> unsubscribe.run());
        emitter.onTimeout(() -> {
            log.info("SSE stream of analysis {} timed out", analysisId);
            unsubscribe.run();
            emitter.complete();
        });
        return emitter;
    }

    /**
     * @return the report once the analysis is over — {@code 202} while it is still running, so a
     *         client that skipped the stream can simply ask again
     */
    @GetMapping("/{analysisId}")
    public ResponseEntity<AnalysisReport> report(@PathVariable String analysisId) {
        return record(analysisId).report()
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.accepted().build());
    }

    private AnalysisRecord record(String analysisId) {
        return analysisStore.find(analysisId).orElseThrow(() -> new AnalysisNotFoundException(analysisId));
    }

    private void validate(AnalyzeRequest request) {
        if (request.from() == null || request.to() == null) {
            throw new InvalidAnalysisRequestException("Analiz aralığı için 'from' ve 'to' zorunlu.");
        }
        if (!request.from().isBefore(request.to())) {
            throw new InvalidAnalysisRequestException("'from' değeri 'to' değerinden önce olmalı.");
        }
    }

    /**
     * Writes events to one client. A client that walked away is an everyday event: the stream is
     * closed and the analysis carries on untouched.
     */
    private static final class EmitterListener implements Consumer<AnalysisEvent> {

        private final SseEmitter emitter;

        private final String analysisId;

        private EmitterListener(SseEmitter emitter, String analysisId) {
            this.emitter = emitter;
            this.analysisId = analysisId;
        }

        @Override
        public void accept(AnalysisEvent event) {
            try {
                emitter.send(SseEmitter.event()
                    .name(event.type().name())
                    .data(event, MediaType.APPLICATION_JSON));
                if (event.terminal()) {
                    emitter.complete();
                }
            }
            catch (IOException | IllegalStateException e) {
                log.info("SSE client of analysis {} is gone, closing the stream: {}", analysisId, e.getMessage());
                emitter.complete();
            }
        }

    }

}
