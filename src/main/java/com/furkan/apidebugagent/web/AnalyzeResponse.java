package com.furkan.apidebugagent.web;

/**
 * What the caller gets back immediately; the analysis itself is still running.
 */
public record AnalyzeResponse(String analysisId) {
}
