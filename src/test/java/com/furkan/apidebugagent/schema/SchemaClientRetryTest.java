package com.furkan.apidebugagent.schema;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.furkan.apidebugagent.config.RetryConfig;
import com.furkan.apidebugagent.sqllog.DemoApiClient;
import com.furkan.apidebugagent.sqllog.DemoApiUnavailableException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(classes = { SchemaClient.class, RetryConfig.class })
class SchemaClientRetryTest {

    @MockitoBean
    private DemoApiClient demoApiClient;

    @Autowired
    private SchemaClient schemaClient;

    @Test
    @SuppressWarnings("unchecked")
    void shouldRetryOnceThenThrowLastErrorWhenDemoApiKeepsFailing() {
        when(demoApiClient.get(anyString(), any(ParameterizedTypeReference.class)))
            .thenThrow(new DemoApiUnavailableException("down", null));

        assertThatThrownBy(schemaClient::fetchForeignKeys).isInstanceOf(DemoApiUnavailableException.class);

        verify(demoApiClient, times(2)).get(anyString(), any(ParameterizedTypeReference.class));
    }

}
