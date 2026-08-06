package com.furkan.apidebugagent.analysis;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FindingTest {

    private static final String QUERY = "select p1_0.id from payment t0 where t0.customer_id = ?";

    @Test
    void shouldProduceSameFindingIdForSameFinding() {
        Finding first = finding("payment.customer_id -> customer.id", QUERY);
        Finding second = finding("payment.customer_id -> customer.id", QUERY);

        assertThat(first.findingId()).isEqualTo(second.findingId());
        assertThat(first.findingId()).isNotBlank();
    }

    @Test
    void shouldProduceDifferentFindingIdWhenForeignKeyDiffers() {
        Finding onCustomer = finding("payment.customer_id -> customer.id", QUERY);
        Finding onOrder = finding("payment.order_id -> orders.id", QUERY);

        assertThat(onCustomer.findingId()).isNotEqualTo(onOrder.findingId());
    }

    @Test
    void shouldProduceDifferentFindingIdWhenNormalizedQueryDiffers() {
        Finding onPayment = finding("payment.customer_id -> customer.id", QUERY);
        Finding onAddress = finding("payment.customer_id -> customer.id",
            "select a1_0.id from address t0 where t0.customer_id = ?");

        assertThat(onPayment.findingId()).isNotEqualTo(onAddress.findingId());
    }

    @Test
    void shouldLeaveExplanationAndSuggestionEmptyOnDetection() {
        Finding detected = finding("payment.customer_id -> customer.id", QUERY);

        assertThat(detected.explanation()).isNull();
        assertThat(detected.suggestion()).isNull();
    }

    @Test
    void shouldFillOnlyEnrichmentFieldsOnWithEnrichment() {
        Finding detected = finding("payment.customer_id -> customer.id", QUERY);
        FixProposal proposal = new FixProposal("@EntityGraph", "tek sorguda çeker", "214 -> 2", "yok", "@BatchSize");

        Finding enriched = detected.withEnrichment("her müşteri için ayrı sorgu", proposal);

        assertThat(enriched.explanation()).isEqualTo("her müşteri için ayrı sorgu");
        assertThat(enriched.suggestion()).isEqualTo(proposal);
        assertThat(enriched).usingRecursiveComparison()
            .ignoringFields("explanation", "suggestion")
            .isEqualTo(detected);
    }

    @Test
    void shouldNotOverwriteEnrichmentThatIsAlreadyPresent() {
        FixProposal first = new FixProposal("@EntityGraph", null, "214 -> 2", null, null);
        Finding enriched = finding("payment.customer_id -> customer.id", QUERY).withEnrichment("ilk açıklama", first);

        Finding again = enriched.withEnrichment("ikinci açıklama",
            new FixProposal("başka", null, "başka", null, null));

        assertThat(again.explanation()).isEqualTo("ilk açıklama");
        assertThat(again.suggestion()).isEqualTo(first);
    }

    private Finding finding(String foreignKey, String normalizedQuery) {
        return new Finding("corr-1", "GET /customers", "customer", "payment", foreignKey, normalizedQuery, 214, 214,
            Confidence.HIGH, List.of("1", "2"), 10L, 11L);
    }

}
