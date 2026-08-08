package com.furkan.apidebugagent.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Status mapping in one place. What reaches the client is a short sentence; the exception and its
 * stacktrace stay in the log.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidAnalysisRequestException.class)
    ProblemDetail handleInvalidRequest(InvalidAnalysisRequestException e) {
        log.debug("Rejected an analysis request: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(AnalysisNotFoundException.class)
    ProblemDetail handleNotFound(AnalysisNotFoundException e) {
        log.debug("Asked for unknown analysis {}", e.analysisId());
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Böyle bir analiz yok.");
    }

}
