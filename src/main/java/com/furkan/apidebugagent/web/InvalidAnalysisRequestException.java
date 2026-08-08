package com.furkan.apidebugagent.web;

/**
 * The request body reached us but does not describe an analysable range. The message is written
 * for the client and is safe to show.
 */
public class InvalidAnalysisRequestException extends RuntimeException {

    public InvalidAnalysisRequestException(String message) {
        super(message);
    }

}
