package com.furkan.apidebugagent.analysis;

import java.util.List;

/**
 * One deterministic N+1 finding: a repeated child query template inside a single request,
 * preceded by a query reading the parent table it points at. Every field is measured — nothing
 * here is inferred by a model.
 *
 * @param foreignKey  human-readable relation, e.g. {@code payment.customer_id -> customer.id}
 * @param sampleBinds first few distinct bind values, shown as examples in the report
 * @param parentSeq   ordering evidence: the parent always ran before {@code firstChildSeq}
 */
public record Finding(
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
        long firstChildSeq) {
}
