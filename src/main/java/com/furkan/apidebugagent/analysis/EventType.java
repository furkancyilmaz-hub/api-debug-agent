package com.furkan.apidebugagent.analysis;

/**
 * What an {@link AnalysisEvent} announces. {@link #REPORT} and {@link #ERROR} are terminal: after
 * either of them the analysis is over and no further event follows.
 */
public enum EventType {

    STAGE_STARTED,
    STAGE_FINISHED,
    REPORT,
    ERROR

}
