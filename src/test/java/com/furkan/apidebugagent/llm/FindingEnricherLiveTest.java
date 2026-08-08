package com.furkan.apidebugagent.llm;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.furkan.apidebugagent.analysis.Confidence;
import com.furkan.apidebugagent.analysis.Finding;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the real model answers {@code prompts/enrich.st} with the schema the enricher reads,
 * and that the merge leaves the measured fields alone.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class FindingEnricherLiveTest {

    @Autowired
    private FindingEnricher findingEnricher;

    @Test
    void shouldEnrichFindingWithExplanationAndFixProposal() {
        Finding finding = new Finding("corr-live-1", "GET /customers", "customer", "payment",
            "payment.customer_id -> customer.id", "select p1_0.id,p1_0.amount from payment t0 where t0.customer_id = ?",
            214, 214, Confidence.HIGH, List.of("1", "2", "3"), 10L, 11L);

        Finding enriched = findingEnricher.enrich(List.of(finding)).get(0);

        assertThat(enriched.explanation()).isNotBlank();
        assertThat(enriched.suggestion()).isNotNull();
        assertThat(enriched.suggestion().action()).isNotBlank();
        assertThat(enriched.suggestion().expectedResult()).containsPattern("\\d");
        assertThat(enriched.repeatCount()).isEqualTo(214);
        assertThat(enriched.confidence()).isEqualTo(Confidence.HIGH);
        assertThat(enriched.findingId()).isEqualTo(finding.findingId());
    }

}
