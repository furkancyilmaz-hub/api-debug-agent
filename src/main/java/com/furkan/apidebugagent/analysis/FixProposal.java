package com.furkan.apidebugagent.analysis;

/**
 * A fix the agent proposes but never applies. Produced by the model on top of a finished
 * {@link Finding}; every field may be {@code null} when the model layer is off or its answer was
 * incomplete.
 *
 * @param expectedResult what the fix should achieve, tied to a number — e.g. {@code "sorgu sayısı
 *                       214 → 2"}. The agent commits to this before the fix so it can be measured
 *                       afterwards.
 */
public record FixProposal(
        String action,
        String rationale,
        String expectedResult,
        String risk,
        String alternatives) {
}
