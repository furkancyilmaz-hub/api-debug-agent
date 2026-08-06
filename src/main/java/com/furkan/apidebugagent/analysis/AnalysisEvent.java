package com.furkan.apidebugagent.analysis;

import java.util.Map;

/**
 * One step of an analysis, as the client sees it. Events are produced by {@link AnalysisService}
 * and handed to a {@code Consumer}; the service knows nothing about SSE or HTTP.
 *
 * <p>{@code payload} carries summary numbers for a stage and the finished report for
 * {@link EventType#REPORT}. Raw log lines and bind values never go into it — this stream ends up
 * in a browser.
 *
 * @param kind       always derived from {@code stage}, never chosen by the caller
 * @param durationMs how long the stage took; {@code null} while it is still running
 */
public record AnalysisEvent(EventType type, Stage stage, StageKind kind, Long durationMs, Object payload) {

    public static AnalysisEvent stageStarted(Stage stage) {
        return new AnalysisEvent(EventType.STAGE_STARTED, stage, stage.kind(), null, null);
    }

    public static AnalysisEvent stageFinished(Stage stage, long durationMs, Map<String, Object> summary) {
        return new AnalysisEvent(EventType.STAGE_FINISHED, stage, stage.kind(), durationMs, Map.copyOf(summary));
    }

    public static AnalysisEvent report(AnalysisReport report) {
        return new AnalysisEvent(EventType.REPORT, null, null, report.durationMs(), report);
    }

    /**
     * @param stage   where the analysis broke off
     * @param message a short line the client can show; internal detail stays in the server log
     */
    public static AnalysisEvent error(Stage stage, String message) {
        return new AnalysisEvent(EventType.ERROR, stage, stage == null ? null : stage.kind(), null,
            Map.of("message", message));
    }

    /** After a terminal event nothing else arrives, so the stream can be closed. */
    public boolean terminal() {
        return type == EventType.REPORT || type == EventType.ERROR;
    }

}
