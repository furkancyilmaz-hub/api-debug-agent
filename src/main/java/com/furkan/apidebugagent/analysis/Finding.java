package com.furkan.apidebugagent.analysis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * One deterministic N+1 finding: a repeated child query template inside a single request,
 * preceded by a query reading the parent table it points at. Every measured field is produced in
 * Java — nothing there is inferred by a model.
 *
 * <p>The last two components are the only ones a model may fill, and only when they are still
 * {@code null}; see {@link #withEnrichment(String, FixProposal)}. A finding is a complete,
 * publishable report with both of them empty.
 *
 * @param findingId   stable identity used to match the model's answer back to this finding
 * @param foreignKey  human-readable relation, e.g. {@code payment.customer_id -> customer.id}
 * @param sampleBinds first few distinct bind values, shown as examples in the report; never sent
 *                    to the model
 * @param parentSeq   ordering evidence: the parent always ran before {@code firstChildSeq}
 * @param explanation model-written prose, {@code null} until enrichment runs
 * @param suggestion  model-written fix proposal, {@code null} until enrichment runs
 */
public record Finding(
        String findingId,
        String correlationId,
        String endpoint,
        String parentTable,
        String childTable,
        String foreignKey,
        String normalizedQuery,
        int repeatCount,
        int distinctBindCount,
        Confidence confidence,
        List<String> sampleBinds,
        long parentSeq,
        long firstChildSeq,
        String explanation,
        FixProposal suggestion) {

    /** How many hex characters of the digest make up a {@code findingId}. */
    private static final int ID_LENGTH = 12;

    /**
     * A freshly detected finding: the identity is derived, the model's two fields are empty.
     */
    public Finding(String correlationId, String endpoint, String parentTable, String childTable, String foreignKey,
            String normalizedQuery, int repeatCount, int distinctBindCount, Confidence confidence,
            List<String> sampleBinds, long parentSeq, long firstChildSeq) {

        this(id(correlationId, foreignKey, normalizedQuery), correlationId, endpoint, parentTable, childTable,
            foreignKey, normalizedQuery, repeatCount, distinctBindCount, confidence, sampleBinds, parentSeq,
            firstChildSeq, null, null);
    }

    /**
     * Identity of a finding, derived from what makes it unique within an analysis. The foreign key
     * belongs in here: one repeated template can carry several foreign-key predicates and yields a
     * separate finding for each, so correlation id and query alone would collide.
     *
     * <p>A digest rather than a random id — the same finding gets the same id on every run, which
     * is what lets the model's answer be matched back deterministically.
     */
    public static String id(String correlationId, String foreignKey, String normalizedQuery) {
        String input = correlationId + "|" + foreignKey + "|" + normalizedQuery;
        return HexFormat.of().formatHex(digest(input)).substring(0, ID_LENGTH);
    }

    /**
     * The same finding with the model's two fields filled in. The merge is one-way on purpose:
     * measured fields are copied untouched and a field that already has a value is never
     * overwritten. This is the only place enrichment may change a finding.
     */
    public Finding withEnrichment(String explanation, FixProposal suggestion) {
        return new Finding(findingId, correlationId, endpoint, parentTable, childTable, foreignKey, normalizedQuery,
            repeatCount, distinctBindCount, confidence, sampleBinds, parentSeq, firstChildSeq,
            this.explanation == null ? explanation : this.explanation,
            this.suggestion == null ? suggestion : this.suggestion);
    }

    private static byte[] digest(String input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JVM but is missing", e);
        }
    }

}
