package com.furkan.apidebugagent.web;

import java.time.Instant;

/**
 * The time range to analyse, as {@code contract.md} §4 defines it.
 */
public record AnalyzeRequest(Instant from, Instant to) {
}
