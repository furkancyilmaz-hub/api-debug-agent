package com.furkan.apidebugagent.analysis;

import java.time.Instant;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Starts an analysis and hands back its id straight away. The work runs on its own thread and
 * reports back through {@link AnalysisStore}; the caller does not wait for it.
 */
@Component
public class AnalysisLauncher {

    private static final Logger log = LoggerFactory.getLogger(AnalysisLauncher.class);

    private final AnalysisService analysisService;

    private final AnalysisStore analysisStore;

    private final Executor analysisExecutor;

    public AnalysisLauncher(AnalysisService analysisService, AnalysisStore analysisStore,
            @Qualifier("analysisExecutor") Executor analysisExecutor) {

        this.analysisService = analysisService;
        this.analysisStore = analysisStore;
        this.analysisExecutor = analysisExecutor;
    }

    public String start(Instant from, Instant to) {
        AnalysisRecord record = analysisStore.create(from, to);
        String analysisId = record.analysisId();
        log.info("Starting analysis {} over [{}, {}]", analysisId, from, to);
        analysisExecutor.execute(() -> run(analysisId, from, to));
        return analysisId;
    }

    private void run(String analysisId, Instant from, Instant to) {
        AnalysisReport report = analysisService.analyze(analysisId, from, to,
            event -> analysisStore.publish(analysisId, event));
        analysisStore.complete(analysisId, report);
        log.info("Analysis {} finished with status {} in {} ms", analysisId, report.status(), report.durationMs());
    }

}
