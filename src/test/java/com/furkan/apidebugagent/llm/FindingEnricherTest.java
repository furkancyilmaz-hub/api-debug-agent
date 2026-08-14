package com.furkan.apidebugagent.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.furkan.apidebugagent.analysis.Confidence;
import com.furkan.apidebugagent.analysis.Finding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FindingEnricherTest {

    private static final Map<String, LlmProperties.ProviderOptions> PROVIDERS = Map.of("anthropic",
            new LlmProperties.ProviderOptions("claude-sonnet-5", 3000));

    private static final LlmProperties ENABLED = new LlmProperties(true, "anthropic", PROVIDERS);
    private static final LlmProperties DISABLED = new LlmProperties(false, "anthropic", PROVIDERS);

    @Mock
    private ChatClientLlmClient llmClient;

    private final JsonResponseParser jsonResponseParser = new JsonResponseParser();

    @Test
    void shouldNotCallModelWhenThereIsNoFinding() {
        List<Finding> enriched = enricher(ENABLED).enrich(List.of());

        assertThat(enriched).isEmpty();
        verifyNoInteractions(llmClient);
    }

    @Test
    void shouldNotCallModelWhenLlmIsDisabled() {
        List<Finding> findings = List.of(finding("f1", 214));

        List<Finding> enriched = enricher(DISABLED).enrich(findings);

        assertThat(enriched).isEqualTo(findings);
        verifyNoInteractions(llmClient);
    }

    @Test
    void shouldFillExplanationAndSuggestionFromModel() {
        Finding finding = finding("f1", 214);
        answerWith("""
            { "items": [
                { "findingId": "%s",
                  "explanation": "Her müşterinin ödemeleri ayrı sorguyla çekiliyor.",
                  "suggestion": { "action": "@EntityGraph", "rationale": "tek sorguda çeker",
                                  "expectedResult": "sorgu sayısı 214 -> 2", "risk": "kartezyen çarpım",
                                  "alternatives": "@BatchSize(size = 50)" } } ] }
            """.formatted(finding.findingId()));

        Finding enriched = enricher(ENABLED).enrich(List.of(finding)).get(0);

        assertThat(enriched.explanation()).isEqualTo("Her müşterinin ödemeleri ayrı sorguyla çekiliyor.");
        assertThat(enriched.suggestion().action()).isEqualTo("@EntityGraph");
        assertThat(enriched.suggestion().rationale()).isEqualTo("tek sorguda çeker");
        assertThat(enriched.suggestion().expectedResult()).isEqualTo("sorgu sayısı 214 -> 2");
        assertThat(enriched.suggestion().risk()).isEqualTo("kartezyen çarpım");
        assertThat(enriched.suggestion().alternatives()).isEqualTo("@BatchSize(size = 50)");
    }

    @Test
    void shouldKeepMeasuredFieldsWhenModelReturnsDifferentValuesForThem() {
        Finding finding = finding("f1", 214);
        answerWith("""
            { "items": [
                { "findingId": "%s", "repeatCount": 1, "distinctBindCount": 1, "confidence": "MEDIUM",
                  "endpoint": "GET /uydurma", "explanation": "açıklama" } ] }
            """.formatted(finding.findingId()));

        Finding enriched = enricher(ENABLED).enrich(List.of(finding)).get(0);

        assertThat(enriched.repeatCount()).isEqualTo(214);
        assertThat(enriched.distinctBindCount()).isEqualTo(214);
        assertThat(enriched.confidence()).isEqualTo(Confidence.HIGH);
        assertThat(enriched.endpoint()).isEqualTo("GET /customers");
        assertThat(enriched.explanation()).isEqualTo("açıklama");
    }

    @Test
    void shouldDropItemWithUnknownFindingIdAndKeepTheRest() {
        Finding first = finding("f1", 214);
        Finding second = finding("f2", 120);
        answerWith("""
            { "items": [
                { "findingId": "uydurulmus-id", "explanation": "rapora girmemeli" },
                { "findingId": "%s", "explanation": "ikinci bulgunun açıklaması" } ] }
            """.formatted(second.findingId()));

        List<Finding> enriched = enricher(ENABLED).enrich(List.of(first, second));

        assertThat(enriched).hasSize(2);
        assertThat(enriched.get(0).explanation()).isNull();
        assertThat(enriched.get(1).explanation()).isEqualTo("ikinci bulgunun açıklaması");
    }

    @Test
    void shouldDropSuggestionWhenExpectedResultIsMissing() {
        Finding finding = finding("f1", 214);
        answerWith("""
            { "items": [
                { "findingId": "%s", "explanation": "açıklama",
                  "suggestion": { "action": "@EntityGraph", "risk": "yok" } } ] }
            """.formatted(finding.findingId()));

        Finding enriched = enricher(ENABLED).enrich(List.of(finding)).get(0);

        assertThat(enriched.explanation()).isEqualTo("açıklama");
        assertThat(enriched.suggestion()).isNull();
    }

    @Test
    void shouldReturnFindingsUnenrichedWhenModelCallFails() {
        Finding finding = finding("f1", 214);
        when(llmClient.ask(eq("enrich"), any())).thenThrow(new LlmResponseException("model exploded"));

        List<Finding> enriched = enricher(ENABLED).enrich(List.of(finding));

        assertThat(enriched).containsExactly(finding);
    }

    @Test
    void shouldReturnFindingsUnenrichedWhenModelAnswerIsNotJson() {
        Finding finding = finding("f1", 214);
        answerWith("Tabii, işte bulgularınızın açıklaması:");

        List<Finding> enriched = enricher(ENABLED).enrich(List.of(finding));

        assertThat(enriched).containsExactly(finding);
    }

    @Test
    void shouldReturnFindingsUnenrichedWhenModelAnswerHasNoItems() {
        Finding finding = finding("f1", 214);
        answerWith("{ \"explanation\": \"yanlış şema\" }");

        List<Finding> enriched = enricher(ENABLED).enrich(List.of(finding));

        assertThat(enriched).containsExactly(finding);
    }

    @Test
    void shouldSendOnlyTheTenMostRepeatedFindingsToModel() {
        List<Finding> findings = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            findings.add(finding("f" + i, 10 + i));
        }
        answerWith("{ \"items\": [] }");

        enricher(ENABLED).enrich(findings);

        String payload = capturedPayload();
        assertThat(payload).doesNotContain(findings.get(0).findingId());
        assertThat(payload).doesNotContain(findings.get(1).findingId());
        for (int i = 2; i < 12; i++) {
            assertThat(payload).contains(findings.get(i).findingId());
        }
    }

    @Test
    void shouldKeepUnsentFindingsInTheReport() {
        List<Finding> findings = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            findings.add(finding("f" + i, 10 + i));
        }
        answerWith("{ \"items\": [] }");

        List<Finding> enriched = enricher(ENABLED).enrich(findings);

        assertThat(enriched).isEqualTo(findings);
    }

    /**
     * A truncated answer now fails inside the client, before the parser sees it. The report is the
     * thing that must survive: same findings, same order, no enrichment.
     */
    @Test
    void shouldReturnFindingsUntouchedWhenTheModelAnswerWasCutOff() {
        List<Finding> findings = List.of(finding("f1", 214), finding("f2", 7));
        when(llmClient.ask(eq("enrich"), any()))
            .thenThrow(new LlmResponseException("Model response was truncated (finishReason=length)"));

        List<Finding> enriched = enricher(ENABLED).enrich(findings);

        assertThat(enriched).isEqualTo(findings);
        assertThat(enriched).allSatisfy(finding -> {
            assertThat(finding.explanation()).isNull();
            assertThat(finding.suggestion()).isNull();
        });
    }

    @Test
    void shouldNotSendBindValuesOrCorrelationIdToModel() {
        Finding finding = finding("secret-correlation-id", 214);
        answerWith("{ \"items\": [] }");

        enricher(ENABLED).enrich(List.of(finding));

        String payload = capturedPayload();
        assertThat(payload).doesNotContain("secret-correlation-id");
        assertThat(payload).doesNotContain("sensitive-bind-value");
        assertThat(payload).contains(finding.findingId(), "payment.customer_id -> customer.id");
    }

    private FindingEnricher enricher(LlmProperties properties) {
        return new FindingEnricher(llmClient, jsonResponseParser, properties);
    }

    private void answerWith(String rawText) {
        when(llmClient.ask(eq("enrich"), any())).thenReturn(new LlmResult(rawText));
    }

    private String capturedPayload() {
        ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.captor();
        verify(llmClient).ask(eq("enrich"), varsCaptor.capture());
        return String.valueOf(varsCaptor.getValue().get("findings"));
    }

    private Finding finding(String correlationId, int repeatCount) {
        return new Finding(correlationId, "GET /customers", "customer", "payment",
            "payment.customer_id -> customer.id", "select p1_0.id from payment t0 where t0.customer_id = ?",
            repeatCount, repeatCount, Confidence.HIGH, List.of("sensitive-bind-value"), 10L, 11L);
    }

}
