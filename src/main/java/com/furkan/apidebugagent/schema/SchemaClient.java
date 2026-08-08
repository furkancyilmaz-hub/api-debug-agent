package com.furkan.apidebugagent.schema;

import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import com.furkan.apidebugagent.sqllog.DemoApiClient;
import com.furkan.apidebugagent.sqllog.DemoApiUnavailableException;

@Component
public class SchemaClient {

    private static final String FOREIGN_KEYS_PATH = "/internal/schema/foreign-keys";

    private final DemoApiClient demoApiClient;

    public SchemaClient(DemoApiClient demoApiClient) {
        this.demoApiClient = demoApiClient;
    }

    @Retryable(retryFor = DemoApiUnavailableException.class, maxAttempts = 2, backoff = @Backoff(delay = 10_000))
    public List<ForeignKey> fetchForeignKeys() {
        return demoApiClient.get(FOREIGN_KEYS_PATH, new ParameterizedTypeReference<List<ForeignKey>>() {
        });
    }

}