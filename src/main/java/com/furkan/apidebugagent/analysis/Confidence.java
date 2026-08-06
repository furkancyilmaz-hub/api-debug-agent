package com.furkan.apidebugagent.analysis;

/**
 * How strongly the evidence supports an N+1 reading. {@code MEDIUM} means the repeated child
 * queries largely re-bind the same key — that is closer to a missing cache than to an N+1.
 */
public enum Confidence {
    HIGH,
    MEDIUM
}
