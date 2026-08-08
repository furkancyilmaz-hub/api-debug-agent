package com.furkan.apidebugagent.web;

/**
 * No analysis with this id — either it never existed or it has been pushed out of the in-memory
 * store by newer ones.
 */
public class AnalysisNotFoundException extends RuntimeException {

    private final String analysisId;

    public AnalysisNotFoundException(String analysisId) {
        super("Unknown analysis id: " + analysisId);
        this.analysisId = analysisId;
    }

    public String analysisId() {
        return analysisId;
    }

}
