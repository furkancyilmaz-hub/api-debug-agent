package com.furkan.apidebugagent.schema;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;

import com.furkan.apidebugagent.sqllog.DemoApiClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchemaClientTest {

    @Mock
    private DemoApiClient demoApiClient;

    @Test
    @SuppressWarnings("unchecked")
    void shouldRequestForeignKeysPathAndReturnResult() {
        List<ForeignKey> expected = List.of(new ForeignKey("payment", "customer_id", "customer", "id"));
        when(demoApiClient.get(eq("/internal/schema/foreign-keys"), any(ParameterizedTypeReference.class)))
            .thenReturn(expected);

        SchemaClient schemaClient = new SchemaClient(demoApiClient);
        List<ForeignKey> result = schemaClient.fetchForeignKeys();

        assertThat(result).isEqualTo(expected);
    }

}