package com.furkan.apidebugagent.analysis;

import java.time.Instant;
import java.util.List;

/**
 * The result of one analysis over a time range. Everything in it is measured — the report is
 * already complete when detection ends, and stays valid when the model layer never runs.
 *
 * @param from  start of the analysed range
 * @param to    end of the analysed range
 * @param error a short, client-safe line; {@code null} unless the status is
 *              {@link AnalysisStatus#FAILED}
 */
public record AnalysisReport(
        String analysisId,
        AnalysisStatus status,
        Instant from,
        Instant to,
        Instant startedAt,
        long durationMs,
        AnalysisCounts counts,
        List<Finding> findings,
        String error) {
}
