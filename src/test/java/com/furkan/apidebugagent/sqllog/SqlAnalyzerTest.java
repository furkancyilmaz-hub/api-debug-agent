package com.furkan.apidebugagent.sqllog;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.furkan.apidebugagent.sqllog.SqlAnalyzer.EqualityPredicate;
import com.furkan.apidebugagent.sqllog.SqlAnalyzer.ParsedSelect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SqlAnalyzerTest {

    @Test
    void shouldResolveMainTableAndSimpleEqualityWithAlias() {
        ParsedSelect result =
                SqlAnalyzer.parse("select p1_0.id,p1_0.amount from payment p1_0 where p1_0.customer_id=?");

        assertThat(result.parsed()).isTrue();
        assertThat(result.table()).isEqualTo("payment");
        assertThat(result.parameterizedEqualities()).containsExactly(
                new EqualityPredicate("payment", "customer_id", 1));
    }

    @Test
    void shouldResolveEqualityOnJoinedTableAlias() {
        ParsedSelect result = SqlAnalyzer.parse(
                "select p1_0.id from payment p1_0 join customer c1_0 on p1_0.customer_id=c1_0.id "
                        + "where c1_0.status=? and p1_0.customer_id=?");

        assertThat(result.parsed()).isTrue();
        assertThat(result.table()).isEqualTo("payment");
        assertThat(result.parameterizedEqualities()).containsExactlyInAnyOrder(
                new EqualityPredicate("customer", "status", 1),
                new EqualityPredicate("payment", "customer_id", 2));
    }

    @Test
    void shouldNumberBindPositionsInSourceOrderNotPredicateOrder() {
        ParsedSelect result = SqlAnalyzer
                .parse("select p1_0.id from payment p1_0 where p1_0.status=? and p1_0.customer_id=?");

        assertThat(result.parameterizedEqualities()).containsExactlyInAnyOrder(
                new EqualityPredicate("payment", "status", 1),
                new EqualityPredicate("payment", "customer_id", 2));
    }

    @Test
    void shouldResolveTableWithoutAliasWhenColumnIsUnqualified() {
        ParsedSelect result = SqlAnalyzer.parse("select id from payment where customer_id=?");

        assertThat(result.parsed()).isTrue();
        assertThat(result.table()).isEqualTo("payment");
        assertThat(result.parameterizedEqualities()).containsExactly(
                new EqualityPredicate("payment", "customer_id", 1));
    }

    @Test
    void shouldReturnUnparsedForNonSelectStatement() {
        ParsedSelect result = SqlAnalyzer.parse("update payment set amount=1 where id=?");

        assertThat(result.parsed()).isFalse();
        assertThat(result.table()).isNull();
        assertThat(result.parameterizedEqualities()).isEmpty();
    }

    @Test
    void shouldReturnUnparsedForMalformedSql() {
        assertThatCode(() -> {
            ParsedSelect result = SqlAnalyzer.parse("select from where this is not $$$ sql <<<");
            assertThat(result.parsed()).isFalse();
        }).doesNotThrowAnyException();
    }

    @Test
    void shouldIgnoreLiteralEqualityWithoutJdbcParameter() {
        ParsedSelect result = SqlAnalyzer
                .parse("select p1_0.id from payment p1_0 where p1_0.status='ACTIVE' and p1_0.customer_id=?");

        assertThat(result.parsed()).isTrue();
        assertThat(result.parameterizedEqualities()).containsExactly(
                new EqualityPredicate("payment", "customer_id", 1));
    }

    @Test
    void shouldIgnoreTopLevelOrWithoutCrashing() {
        ParsedSelect result = SqlAnalyzer
                .parse("select p1_0.id from payment p1_0 where p1_0.status='ACTIVE' or p1_0.customer_id=?");

        assertThat(result.parsed()).isTrue();
        assertThat(result.table()).isEqualTo("payment");
        assertThat(result.parameterizedEqualities()).isEmpty();
    }

    @Test
    void shouldReturnParsedTrueWithEmptyPredicatesWhenNoWhereClause() {
        ParsedSelect result = SqlAnalyzer.parse("select id from payment");

        assertThat(result.parsed()).isTrue();
        assertThat(result.table()).isEqualTo("payment");
        assertThat(result.parameterizedEqualities()).isEmpty();
    }

    @Test
    void shouldLowercaseTableAndColumnNames() {
        ParsedSelect result = SqlAnalyzer.parse("select P1_0.ID from PAYMENT P1_0 where P1_0.CUSTOMER_ID=?");

        assertThat(result.table()).isEqualTo("payment");
        assertThat(result.parameterizedEqualities()).containsExactly(
                new EqualityPredicate("payment", "customer_id", 1));
    }

    @Test
    void shouldReturnEmptyListNotNullForNoPredicates() {
        ParsedSelect result = SqlAnalyzer.parse("select id from payment where 1=1");

        assertThat(result.parameterizedEqualities()).isInstanceOf(List.class).isEmpty();
    }
}
