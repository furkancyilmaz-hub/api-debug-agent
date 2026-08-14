package com.furkan.apidebugagent.llm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.furkan.apidebugagent.analysis.Confidence;
import com.furkan.apidebugagent.analysis.Finding;
import com.furkan.apidebugagent.analysis.FixProposal;

/**
 * Adds prose and a fix proposal on top of findings that are already complete. One model call per
 * analysis, and the model is not part of detection: it cannot add, drop or re-score a finding, it
 * only fills the two empty fields of {@link Finding}.
 *
 * <p>Everything here is best-effort. When the model layer is off, when there is nothing to
 * enrich, or when the call fails, the findings come back exactly as they went in.
 */
@Component
public class FindingEnricher {

    private static final Logger log = LoggerFactory.getLogger(FindingEnricher.class);

    private static final String PROMPT_NAME = "enrich";

    /** Findings sent to the model in one call; the rest stay in the report unenriched. */
    private static final int MAX_ENRICHED = 10;

    private final ChatClientLlmClient llmClient;

    private final JsonResponseParser jsonResponseParser;

    private final LlmProperties llmProperties;

    /** Own mapper, like {@link JsonResponseParser}: the auto-configured one is Jackson 3. */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FindingEnricher(ChatClientLlmClient llmClient, JsonResponseParser jsonResponseParser,
            LlmProperties llmProperties) {
        this.llmClient = llmClient;
        this.jsonResponseParser = jsonResponseParser;
        this.llmProperties = llmProperties;
    }

    /**
     * @param findings findings as detection left them
     * @return the same findings in the same order, some of them carrying an explanation and a fix
     *         proposal; never fewer, never more
     */
    public List<Finding> enrich(List<Finding> findings) {
        if (findings == null || findings.isEmpty()) {
            return List.of();
        }
        if (!llmProperties.enabled()) {
            log.debug("LLM layer is disabled; returning {} findings unenriched", findings.size());
            return findings;
        }

        List<Finding> selected = mostRepeated(findings);
        LlmResult result = null;
        try {
            String payload = objectMapper.writeValueAsString(selected.stream().map(EnrichmentInput::of).toList());
            result = llmClient.ask(PROMPT_NAME, Map.of("findings", payload));
            return merge(findings, readEnrichments(jsonResponseParser.parse(result.rawText()), selected));
        }
        // Enrichment is a side channel: whatever went wrong — disabled mid-flight, transport,
        // malformed answer — the measured report still stands and must be returned. The finish
        // reason rides along because a malformed answer is usually a cut-off one.
        catch (JsonProcessingException | RuntimeException e) {
            log.warn("Finding enrichment failed (finishReason={}); returning {} findings unenriched",
                result == null ? "n/a" : result.finishReason(), findings.size(), e);
            return findings;
        }
    }

    /** The loudest findings first — a token budget spent on the 214-query one, not the 5. */
    private List<Finding> mostRepeated(List<Finding> findings) {
        return findings.stream()
            .sorted(Comparator.comparingInt(Finding::repeatCount).reversed())
            .limit(MAX_ENRICHED)
            .toList();
    }

    /**
     * Reads the model's answer, keeping only items whose {@code findingId} we sent. An invented id
     * cannot reach the report.
     */
    private Map<String, Enrichment> readEnrichments(JsonNode root, List<Finding> selected) {
        Map<String, Finding> sent = new HashMap<>();
        for (Finding finding : selected) {
            sent.put(finding.findingId(), finding);
        }

        JsonNode items = root.path("items");
        if (!items.isArray()) {
            log.warn("Model answer has no items array; returning findings unenriched");
            return Map.of();
        }

        Map<String, Enrichment> enrichments = new HashMap<>();
        int dropped = 0;
        for (JsonNode item : items) {
            String findingId = text(item, "findingId");
            if (findingId == null || !sent.containsKey(findingId)) {
                dropped++;
                continue;
            }
            enrichments.put(findingId, new Enrichment(text(item, "explanation"), suggestion(item.path("suggestion"))));
        }
        if (dropped > 0) {
            log.warn("Dropped {} enrichment item(s) with an unknown findingId", dropped);
        }
        return enrichments;
    }

    /**
     * A proposal without a concrete expected result is not a proposal — the agent has to commit to
     * a number before the fix so the claim can be measured afterwards.
     */
    private FixProposal suggestion(JsonNode node) {
        String action = text(node, "action");
        String expectedResult = text(node, "expectedResult");
        if (action == null || expectedResult == null) {
            return null;
        }
        return new FixProposal(action, text(node, "rationale"), expectedResult, text(node, "risk"),
            text(node, "alternatives"));
    }

    private List<Finding> merge(List<Finding> findings, Map<String, Enrichment> enrichments) {
        List<Finding> merged = new ArrayList<>(findings.size());
        for (Finding finding : findings) {
            Enrichment enrichment = enrichments.get(finding.findingId());
            merged.add(enrichment == null ? finding
                : finding.withEnrichment(enrichment.explanation(), enrichment.suggestion()));
        }
        return List.copyOf(merged);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            return null;
        }
        return value.asText().trim();
    }

    /**
     * What the model gets to see: finding fields only. No raw logs, no bind values, no correlation
     * id — the model has nothing to reason from except what was already measured.
     */
    private record EnrichmentInput(
            String findingId,
            String endpoint,
            String parentTable,
            String childTable,
            String foreignKey,
            String normalizedQuery,
            int repeatCount,
            int distinctBindCount,
            Confidence confidence) {

        static EnrichmentInput of(Finding finding) {
            return new EnrichmentInput(finding.findingId(), finding.endpoint(), finding.parentTable(),
                finding.childTable(), finding.foreignKey(), finding.normalizedQuery(), finding.repeatCount(),
                finding.distinctBindCount(), finding.confidence());
        }
    }

    /** The two fields the model is allowed to produce, before they are merged back. */
    private record Enrichment(String explanation, FixProposal suggestion) {
    }

}
