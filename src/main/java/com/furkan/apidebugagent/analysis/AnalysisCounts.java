package com.furkan.apidebugagent.analysis;

/**
 * What the analysis went through, in numbers. On a failed analysis these are the counts reached
 * before it broke off.
 */
public record AnalysisCounts(int logLines, int requests, int queries, int findings) {
}
