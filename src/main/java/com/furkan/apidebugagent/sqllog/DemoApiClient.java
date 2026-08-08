package com.furkan.apidebugagent.sqllog;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class DemoApiClient {

    private final RestClient restClient;

    public DemoApiClient(RestClient.Builder restClientBuilder, TargetProperties targetProperties) {
        this.restClient = restClientBuilder.baseUrl(targetProperties.baseUrl()).build();
    }

    public <T> T get(String path, ParameterizedTypeReference<T> responseType) {
        try {
            return restClient.get()
                .uri(path)
                .retrieve()
                .body(responseType);
        }
        catch (RestClientException e) {
            throw new DemoApiUnavailableException("Failed to call demo-api GET " + path + ": " + e.getMessage(), e);
        }
    }

}