package com.furkan.apidebugagent.sqllog;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatementAssemblerTest {

    private final StatementAssembler assembler = new StatementAssembler();

    @Test
    void shouldGroupByCorrelationIdAndThreadAndAttachBinds() {
        Instant t0 = Instant.parse("2024-01-01T10:00:00.000Z");
        List<LogLine> lines = List.of(
            new LogLine(0, "c1", t0, "thread-a", "org.hibernate.SQL",
                "select p1_0.id from payment p1_0 where p1_0.customer_id=?"),
            new LogLine(1, "c1", t0, "thread-a", "org.hibernate.orm.jdbc.bind",
                "binding parameter (1:BIGINT) <- [42]"),
            new LogLine(2, "c1", t0, "thread-b", "org.hibernate.SQL",
                "select c1_0.id from customer c1_0 where c1_0.id=?"),
            new LogLine(3, "c1", t0, "thread-b", "org.hibernate.orm.jdbc.bind",
                "binding parameter (1:BIGINT) <- [7]")
        );

        List<ExecutedQuery> result = assembler.assemble(lines);

        assertThat(result).hasSize(2);
        ExecutedQuery paymentQuery = result.stream().filter(q -> q.rawSql().contains("from payment")).findFirst()
            .orElseThrow();
        assertThat(paymentQuery.binds()).containsExactly(new BindParameter(1, "BIGINT", "42"));
        ExecutedQuery customerQuery = result.stream().filter(q -> q.rawSql().contains("from customer")).findFirst()
            .orElseThrow();
        assertThat(customerQuery.binds()).containsExactly(new BindParameter(1, "BIGINT", "7"));
    }

    @Test
    void shouldUseSeqAsTiebreakerWhenTimestampsAreEqual() {
        Instant sameInstant = Instant.parse("2024-01-01T10:00:00.000Z");
        List<LogLine> lines = List.of(
            new LogLine(5, "c1", sameInstant, "thread-a", "org.hibernate.SQL", "select 1 from a"),
            new LogLine(2, "c1", sameInstant, "thread-a", "org.hibernate.SQL", "select 2 from b")
        );

        List<ExecutedQuery> result = assembler.assemble(lines);

        assertThat(result).extracting(ExecutedQuery::rawSql).containsExactly("select 2 from b", "select 1 from a");
        assertThat(result).extracting(ExecutedQuery::seq).containsExactly(0, 1);
    }

    @Test
    void shouldIgnoreLinesFromOtherLoggers() {
        Instant t0 = Instant.parse("2024-01-01T10:00:00.000Z");
        List<LogLine> lines = List.of(
            new LogLine(0, "c1", t0, "thread-a", "com.zaxxer.hikari.pool.HikariPool", "connection acquired"),
            new LogLine(1, "c1", t0, "thread-a", "org.hibernate.SQL", "select 1 from a")
        );

        List<ExecutedQuery> result = assembler.assemble(lines);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).rawSql()).isEqualTo("select 1 from a");
    }

    @Test
    void shouldIgnoreOrphanBindBeforeAnySqlLine() {
        Instant t0 = Instant.parse("2024-01-01T10:00:00.000Z");
        List<LogLine> lines = List.of(
            new LogLine(0, "c1", t0, "thread-a", "org.hibernate.orm.jdbc.bind", "binding parameter (1:BIGINT) <- [1]"),
            new LogLine(1, "c1", t0, "thread-a", "org.hibernate.SQL", "select 1 from a")
        );

        List<ExecutedQuery> result = assembler.assemble(lines);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).binds()).isEmpty();
    }

}
