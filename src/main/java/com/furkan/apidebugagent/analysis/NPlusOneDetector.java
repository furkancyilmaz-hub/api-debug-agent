package com.furkan.apidebugagent.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.furkan.apidebugagent.schema.ForeignKey;
import com.furkan.apidebugagent.schema.ForeignKeyCache;
import com.furkan.apidebugagent.sqllog.BindParameter;
import com.furkan.apidebugagent.sqllog.ExecutedQuery;
import com.furkan.apidebugagent.sqllog.RequestInfo;
import com.furkan.apidebugagent.sqllog.SqlAnalyzer;
import com.furkan.apidebugagent.sqllog.SqlAnalyzer.EqualityPredicate;
import com.furkan.apidebugagent.sqllog.SqlAnalyzer.ParsedSelect;
import com.furkan.apidebugagent.sqllog.SqlNormalizer;

/**
 * Deterministic N+1 detection. Given the queries executed during a set of requests, reports every
 * repeated child-query template that is preceded, within the same request, by a query reading the
 * parent table its foreign key points at.
 *
 * <p>No model is involved and nothing is guessed: the repeat count, the distinct bind count and
 * the ordering evidence all come from the logs. The repetition threshold comes from configuration,
 * never from a constant here.
 */
@Component
public class NPlusOneDetector {

    private final ForeignKeyCache foreignKeyCache;

    private final NPlusOneProperties properties;

    public NPlusOneDetector(ForeignKeyCache foreignKeyCache, NPlusOneProperties properties) {
        this.foreignKeyCache = foreignKeyCache;
        this.properties = properties;
    }

    /**
     * @param queries  executed SELECTs, in any order; grouped and re-sorted by {@code seq} here
     * @param requests request metadata used only to label findings with an endpoint
     * @return findings ordered by request, then by the first execution of the child template
     */
    public List<Finding> detect(List<ExecutedQuery> queries, List<RequestInfo> requests) {
        if (queries == null || queries.isEmpty()) {
            return List.of();
        }

        Map<String, String> endpointsByCorrelationId = endpoints(requests);
        // The same raw SQL repeats hundreds of times in an N+1; parse and normalize it once.
        Map<String, ParsedSelect> parseCache = new HashMap<>();
        Map<String, String> normalizeCache = new HashMap<>();

        Map<String, List<ExecutedQuery>> byCorrelationId = new LinkedHashMap<>();
        for (ExecutedQuery query : queries) {
            byCorrelationId.computeIfAbsent(query.correlationId(), k -> new ArrayList<>()).add(query);
        }

        List<Finding> findings = new ArrayList<>();
        for (Map.Entry<String, List<ExecutedQuery>> entry : byCorrelationId.entrySet()) {
            List<ExecutedQuery> group = new ArrayList<>(entry.getValue());
            group.sort(Comparator.comparingInt(ExecutedQuery::seq));
            findings.addAll(detectInRequest(entry.getKey(), endpointsByCorrelationId.get(entry.getKey()), group,
                parseCache, normalizeCache));
        }
        return List.copyOf(findings);
    }

    private Map<String, String> endpoints(List<RequestInfo> requests) {
        if (requests == null) {
            return Map.of();
        }
        Map<String, String> out = new HashMap<>();
        for (RequestInfo request : requests) {
            // RequestInfo carries no query string, so the endpoint is method + path.
            out.putIfAbsent(request.correlationId(), request.method() + " " + request.path());
        }
        return out;
    }

    private List<Finding> detectInRequest(String correlationId, String endpoint, List<ExecutedQuery> group,
            Map<String, ParsedSelect> parseCache, Map<String, String> normalizeCache) {

        List<ExecutedQuery> parseable = new ArrayList<>();
        for (ExecutedQuery query : group) {
            if (parse(query, parseCache).parsed()) {
                parseable.add(query);
            }
        }

        Map<String, List<ExecutedQuery>> byTemplate = new LinkedHashMap<>();
        for (ExecutedQuery query : parseable) {
            byTemplate.computeIfAbsent(normalize(query, normalizeCache), k -> new ArrayList<>()).add(query);
        }

        List<Finding> findings = new ArrayList<>();
        for (Map.Entry<String, List<ExecutedQuery>> entry : byTemplate.entrySet()) {
            findings.addAll(detectInTemplate(correlationId, endpoint, entry.getKey(), entry.getValue(), parseable,
                parseCache));
        }
        findings.sort(Comparator.comparingLong(Finding::firstChildSeq));
        return findings;
    }

    private List<Finding> detectInTemplate(String correlationId, String endpoint, String normalizedQuery,
            List<ExecutedQuery> runs, List<ExecutedQuery> parseable, Map<String, ParsedSelect> parseCache) {

        // Predicate order out of the analyzer is stack order, not source order — sort so the
        // findings of a template are emitted deterministically.
        List<EqualityPredicate> predicates = new ArrayList<>(parse(runs.get(0), parseCache).parameterizedEqualities());
        predicates.sort(Comparator.comparing(EqualityPredicate::column)
            .thenComparingInt(EqualityPredicate::parameterIndex));

        Set<ExecutedQuery> templateRuns = new LinkedHashSet<>(runs);
        List<Finding> findings = new ArrayList<>();
        for (EqualityPredicate predicate : predicates) {
            Optional<ForeignKey> foreignKey = foreignKeyCache.byChildColumn(predicate.table(), predicate.column());
            if (foreignKey.isPresent()) {
                buildFinding(correlationId, endpoint, normalizedQuery, runs, parseable, parseCache, templateRuns,
                    predicate, foreignKey.get()).ifPresent(findings::add);
            }
        }
        return findings;
    }

    private Optional<Finding> buildFinding(String correlationId, String endpoint, String normalizedQuery,
            List<ExecutedQuery> runs, List<ExecutedQuery> parseable, Map<String, ParsedSelect> parseCache,
            Set<ExecutedQuery> templateRuns, EqualityPredicate predicate, ForeignKey foreignKey) {

        List<String> bindValues = new ArrayList<>();
        long firstChildSeq = -1;
        for (ExecutedQuery run : runs) {
            // Resolve the bind position from this run's own parse rather than reusing the first
            // run's — never take the first bind blindly.
            Optional<String> value = bindPosition(run, predicate, parseCache).flatMap(index -> bindValue(run, index));
            if (value.isPresent()) {
                if (bindValues.isEmpty()) {
                    firstChildSeq = run.seq();
                }
                bindValues.add(value.get());
            }
        }

        int repeatCount = bindValues.size();
        if (repeatCount < properties.minRepeat()) {
            return Optional.empty();
        }

        Optional<ExecutedQuery> parent =
            findParent(parseable, parseCache, foreignKey.parentTable(), firstChildSeq, templateRuns);
        if (parent.isEmpty()) {
            return Optional.empty();
        }

        Set<String> distinctBinds = new LinkedHashSet<>(bindValues);
        return Optional.of(new Finding(
            correlationId,
            endpoint,
            foreignKey.parentTable(),
            foreignKey.childTable(),
            relation(foreignKey),
            normalizedQuery,
            repeatCount,
            distinctBinds.size(),
            confidence(repeatCount, distinctBinds.size()),
            List.copyOf(distinctBinds),
            parent.get().seq(),
            firstChildSeq));
    }

    /**
     * The closest query before the child's first run that reads the parent table. The ordering
     * check is what eliminates false positives, so it is applied on {@code seq}, not on position.
     */
    private Optional<ExecutedQuery> findParent(List<ExecutedQuery> parseable, Map<String, ParsedSelect> parseCache,
            String parentTable, long firstChildSeq, Set<ExecutedQuery> templateRuns) {

        ExecutedQuery closest = null;
        for (ExecutedQuery candidate : parseable) {
            if (candidate.seq() < firstChildSeq && !templateRuns.contains(candidate)
                    && parentTable.equals(parse(candidate, parseCache).table())
                    && (closest == null || candidate.seq() > closest.seq())) {
                closest = candidate;
            }
        }
        return Optional.ofNullable(closest);
    }

    private Optional<Integer> bindPosition(ExecutedQuery query, EqualityPredicate predicate,
            Map<String, ParsedSelect> parseCache) {
        for (EqualityPredicate candidate : parse(query, parseCache).parameterizedEqualities()) {
            if (candidate.table().equals(predicate.table()) && candidate.column().equals(predicate.column())) {
                return Optional.of(candidate.parameterIndex());
            }
        }
        return Optional.empty();
    }

    private Optional<String> bindValue(ExecutedQuery query, int parameterIndex) {
        if (query.binds() == null) {
            return Optional.empty();
        }
        for (BindParameter bind : query.binds()) {
            if (bind.index() == parameterIndex) {
                return Optional.ofNullable(bind.value());
            }
        }
        return Optional.empty();
    }

    /**
     * Mostly-repeating binds mean the same id is fetched over and over — a missing cache rather
     * than an N+1. The finding still stands, the confidence drops.
     */
    private Confidence confidence(int repeatCount, int distinctBindCount) {
        return distinctBindCount * 10 >= repeatCount * 9 ? Confidence.HIGH : Confidence.MEDIUM;
    }

    private String relation(ForeignKey foreignKey) {
        return foreignKey.childTable() + "." + foreignKey.childColumn() + " -> " + foreignKey.parentTable() + "."
            + foreignKey.parentColumn();
    }

    private ParsedSelect parse(ExecutedQuery query, Map<String, ParsedSelect> parseCache) {
        return parseCache.computeIfAbsent(query.rawSql(), SqlAnalyzer::parse);
    }

    private String normalize(ExecutedQuery query, Map<String, String> normalizeCache) {
        return normalizeCache.computeIfAbsent(query.rawSql(), SqlNormalizer::normalize);
    }

}
