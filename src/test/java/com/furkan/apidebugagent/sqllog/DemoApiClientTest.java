package com.furkan.apidebugagent.sqllog;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoApiClientTest {

    private static final String BASE_URL = "http://demo-api.test";

    @Test
    void shouldReturnTypedBodyOnSuccessfulGet() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(MockRestRequestMatchers.requestTo(BASE_URL + "/ping"))
            .andRespond(MockRestResponseCreators.withSuccess("{\"value\":\"pong\"}", MediaType.APPLICATION_JSON));

        DemoApiClient client = new DemoApiClient(builder, new TargetProperties(BASE_URL));

        Map<String, String> result = client.get("/ping", new ParameterizedTypeReference<Map<String, String>>() {
        });

        assertThat(result).containsEntry("value", "pong");
        server.verify();
    }

    @Test
    void shouldWrapServerErrorAsDemoApiUnavailableException() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(MockRestRequestMatchers.requestTo(BASE_URL + "/boom"))
            .andRespond(MockRestResponseCreators.withServerError());

        DemoApiClient client = new DemoApiClient(builder, new TargetProperties(BASE_URL));

        assertThatThrownBy(() -> client.get("/boom", new ParameterizedTypeReference<Void>() {
        })).isInstanceOf(DemoApiUnavailableException.class);
    }

    @Test
    void shouldWrapConnectionFailureAsDemoApiUnavailableException() {
        RestClient.Builder builder = RestClient.builder();
        DemoApiClient client = new DemoApiClient(builder, new TargetProperties("http://localhost:1"));

        assertThatThrownBy(() -> client.get("/anything", new ParameterizedTypeReference<Void>() {
        })).isInstanceOf(DemoApiUnavailableException.class);
    }

}