package com.furkan.apidebugagent.schema;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.furkan.apidebugagent.sqllog.DemoApiUnavailableException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForeignKeyCacheTest {

    @Mock
    private SchemaClient schemaClient;

    private static final ForeignKey PAYMENT_CUSTOMER_FK = new ForeignKey("payment", "customer_id", "customer", "id");

    @Test
    void shouldPopulateCacheOnApplicationReady() {
        when(schemaClient.fetchForeignKeys()).thenReturn(List.of(PAYMENT_CUSTOMER_FK));
        ForeignKeyCache cache = new ForeignKeyCache(schemaClient);

        cache.onApplicationReady();

        assertThat(cache.byChildColumn("payment", "customer_id")).contains(PAYMENT_CUSTOMER_FK);
    }

    @Test
    void shouldSwallowUnavailableExceptionOnApplicationReadyAndNotThrow() {
        when(schemaClient.fetchForeignKeys()).thenThrow(new DemoApiUnavailableException("down", null));
        ForeignKeyCache cache = new ForeignKeyCache(schemaClient);

        assertThatCode(cache::onApplicationReady).doesNotThrowAnyException();
    }

    @Test
    void shouldNormalizeCaseOnLookup() {
        when(schemaClient.fetchForeignKeys()).thenReturn(List.of(PAYMENT_CUSTOMER_FK));
        ForeignKeyCache cache = new ForeignKeyCache(schemaClient);
        cache.refresh();

        assertThat(cache.byChildColumn("PAYMENT", "Customer_ID")).contains(PAYMENT_CUSTOMER_FK);
    }

    @Test
    void shouldRefreshOnceWhenCacheEmptyThenSucceed() {
        when(schemaClient.fetchForeignKeys()).thenReturn(List.of(PAYMENT_CUSTOMER_FK));
        ForeignKeyCache cache = new ForeignKeyCache(schemaClient);

        Optional<ForeignKey> result = cache.byChildColumn("payment", "customer_id");

        assertThat(result).contains(PAYMENT_CUSTOMER_FK);
        verify(schemaClient, times(1)).fetchForeignKeys();
    }

    @Test
    void shouldThrowSchemaUnavailableWhenCacheEmptyAndRefreshFails() {
        when(schemaClient.fetchForeignKeys()).thenThrow(new DemoApiUnavailableException("down", null));
        ForeignKeyCache cache = new ForeignKeyCache(schemaClient);

        assertThatThrownBy(() -> cache.byChildColumn("payment", "customer_id"))
            .isInstanceOf(SchemaUnavailableException.class);
    }

    @Test
    void shouldThrowSchemaUnavailableWhenRefreshSucceedsButStillEmpty() {
        when(schemaClient.fetchForeignKeys()).thenReturn(List.of());
        ForeignKeyCache cache = new ForeignKeyCache(schemaClient);

        assertThatThrownBy(() -> cache.byChildColumn("payment", "customer_id"))
            .isInstanceOf(SchemaUnavailableException.class);
    }

    @Test
    void shouldReturnEmptyOptionalWhenColumnNotAForeignKey() {
        when(schemaClient.fetchForeignKeys()).thenReturn(List.of(PAYMENT_CUSTOMER_FK));
        ForeignKeyCache cache = new ForeignKeyCache(schemaClient);
        cache.refresh();

        assertThat(cache.byChildColumn("payment", "amount")).isEmpty();
    }

}
