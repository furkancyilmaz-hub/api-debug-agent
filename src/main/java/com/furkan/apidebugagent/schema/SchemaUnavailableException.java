package com.furkan.apidebugagent.schema;

public class SchemaUnavailableException extends RuntimeException {

    public SchemaUnavailableException(String message) {
        super(message);
    }

    public SchemaUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

}