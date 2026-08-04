package com.furkan.apidebugagent.sqllog;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class StatementAssembler {

    private static final Logger log = LoggerFactory.getLogger(StatementAssembler.class);

    private static final String SQL_LOGGER = "org.hibernate.SQL";
    private static final String BIND_LOGGER = "org.hibernate.orm.jdbc.bind";
    private static final Pattern BIND_PATTERN = Pattern.compile("binding parameter \\((\\d+):(\\w+)\\) <- \\[(.*)]");

    public List<ExecutedQuery> assemble(List<LogLine> lines) {
        List<LogLine> sorted = lines.stream()
            .sorted(Comparator.comparingLong(LogLine::seq))
            .toList();

        Map<GroupKey, List<LogLine>> groups = new LinkedHashMap<>();
        for (LogLine line : sorted) {
            groups.computeIfAbsent(new GroupKey(line.correlationId(), line.thread()), k -> new ArrayList<>())
                .add(line);
        }

        Map<String, List<InProgressQuery>> byCorrelationId = new LinkedHashMap<>();
        for (List<LogLine> group : groups.values()) {
            InProgressQuery current = null;
            for (LogLine line : group) {
                if (SQL_LOGGER.equals(line.logger())) {
                    current = new InProgressQuery(line.correlationId(), line.seq(), line.timestamp(), line.message());
                    byCorrelationId.computeIfAbsent(line.correlationId(), k -> new ArrayList<>()).add(current);
                }
                else if (BIND_LOGGER.equals(line.logger())) {
                    if (current == null) {
                        log.debug("Ignoring orphan bind line before any SQL line: correlationId={}, thread={}",
                            line.correlationId(), line.thread());
                        continue;
                    }
                    parseBind(line.message()).ifPresent(current.binds::add);
                }
                // other loggers are ignored
            }
        }

        List<ExecutedQuery> result = new ArrayList<>();
        for (List<InProgressQuery> queries : byCorrelationId.values()) {
            List<InProgressQuery> ordered = queries.stream()
                .sorted(Comparator.comparing(InProgressQuery::timestamp)
                    .thenComparingLong(InProgressQuery::sourceSeq))
                .toList();
            int seq = 0;
            for (InProgressQuery q : ordered) {
                result.add(new ExecutedQuery(q.correlationId, seq++, q.timestamp, q.rawSql, List.copyOf(q.binds)));
            }
        }
        return result;
    }

    private Optional<BindParameter> parseBind(String message) {
        Matcher matcher = BIND_PATTERN.matcher(message);
        if (!matcher.find()) {
            return Optional.empty();
        }
        int index = Integer.parseInt(matcher.group(1));
        String type = matcher.group(2);
        String value = matcher.group(3);
        return Optional.of(new BindParameter(index, type, value));
    }

    private record GroupKey(String correlationId, String thread) {
    }

    private static final class InProgressQuery {
        private final String correlationId;
        private final long sourceSeq;
        private final Instant timestamp;
        private final String rawSql;
        private final List<BindParameter> binds = new ArrayList<>();

        private InProgressQuery(String correlationId, long sourceSeq, Instant timestamp, String rawSql) {
            this.correlationId = correlationId;
            this.sourceSeq = sourceSeq;
            this.timestamp = timestamp;
            this.rawSql = rawSql;
        }

        private long sourceSeq() {
            return sourceSeq;
        }

        private Instant timestamp() {
            return timestamp;
        }
    }

}