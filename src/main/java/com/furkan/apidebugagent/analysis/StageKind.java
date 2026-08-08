package com.furkan.apidebugagent.analysis;

/**
 * Who does the work of a stage. The distinction is on the wire so the client can tell the single
 * model call apart from everything the agent measured itself.
 */
public enum StageKind {

    /** Deterministic work in Java: fetching, parsing, detecting. */
    LOCAL,

    /** The one model call of an analysis. */
    MODEL

}
