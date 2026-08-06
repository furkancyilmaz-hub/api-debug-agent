package com.furkan.apidebugagent.sqllog;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SqlNormalizerTest {

    @Test
    void shouldNormalizeWhitespaceAndCase() {
        String normalized = SqlNormalizer.normalize("  SELECT   id   FROM   Payment  ");

        assertThat(normalized).isEqualTo(normalized.toLowerCase());
        assertThat(normalized).doesNotContain("  ");
        assertThat(normalized).isEqualTo(normalized.trim());
    }

    @Test
    void shouldCanonicalizeAliasesSoDifferentRunsMatch() {
        String first = SqlNormalizer.normalize("select p1_0.id,p1_0.amount from payment p1_0 where p1_0.customer_id=?");
        String second = SqlNormalizer.normalize("select p2_0.id,p2_0.amount from payment p2_0 where p2_0.customer_id=?");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void shouldCanonicalizeAliasesAcrossJoinedTables() {
        String first = SqlNormalizer.normalize(
                "select p1_0.id from payment p1_0 join customer c1_0 on p1_0.customer_id=c1_0.id "
                        + "where c1_0.status=? and p1_0.customer_id=?");
        String second = SqlNormalizer.normalize(
                "select p2_0.id from payment p2_0 join customer c2_0 on p2_0.customer_id=c2_0.id "
                        + "where c2_0.status=? and p2_0.customer_id=?");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void shouldReplaceLiteralConstantAlongsideBindParameter() {
        String normalized = SqlNormalizer.normalize(
                "select p1_0.id from payment p1_0 where p1_0.status='ACTIVE' and p1_0.customer_id=?");

        assertThat(normalized).doesNotContain("active");
        assertThat(normalized).contains("?");
    }

    @Test
    void shouldNotThrowOnUnparseableSql() {
        assertThatCode(() -> SqlNormalizer.normalize("select from where this is not $$$ sql <<<"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldHandleBareTableWithoutAliasWithoutError() {
        String normalized = SqlNormalizer.normalize("select id from payment where customer_id=?");

        assertThat(normalized).isEqualTo("select id from payment where customer_id = ?");
    }

    @Test
    void shouldCollapseManyStructurallyIdenticalChildQueriesToSingleTemplate() {
        List<String> variants = List.of(
                "select p1_0.id,p1_0.amount from payment p1_0 where p1_0.customer_id=?",
                "select p2_0.id,p2_0.amount from payment p2_0 where p2_0.customer_id=?",
                "select p3_0.id,p3_0.amount from payment p3_0 where p3_0.customer_id=?",
                "select p11_0.id,p11_0.amount from payment p11_0 where p11_0.customer_id=?",
                "select p47_0.id,p47_0.amount from payment p47_0 where p47_0.customer_id=?");

        Set<String> templates = variants.stream().map(SqlNormalizer::normalize).collect(Collectors.toSet());

        assertThat(templates).hasSize(1);
    }
}
