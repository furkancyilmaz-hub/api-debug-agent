package com.furkan.apidebugagent.analysis;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The four stages of an analysis, in the order they run. The report is already complete after
 * {@link #DETECTION}; {@link #ENRICHMENT} only fills the two model-written fields of a finding and
 * is skipped entirely when there is nothing to enrich or the model layer is off.
 *
 * <p>The wire names are fixed by {@code contract.md} §4 and shared with {@code debug-console};
 * they are not derived from the constant names.
 */
public enum Stage {

    LOGS("loglar", StageKind.LOCAL),
    PARSING("ayrıştırma", StageKind.LOCAL),
    DETECTION("tespit", StageKind.LOCAL),
    ENRICHMENT("zenginleştirme", StageKind.MODEL);

    private final String wireName;

    private final StageKind kind;

    Stage(String wireName, StageKind kind) {
        this.wireName = wireName;
        this.kind = kind;
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }

    /** Whose work this stage is; an event never carries a hand-picked kind. */
    public StageKind kind() {
        return kind;
    }

}
