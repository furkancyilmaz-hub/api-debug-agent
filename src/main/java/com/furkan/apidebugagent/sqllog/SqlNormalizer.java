package com.furkan.apidebugagent.sqllog;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.DateValue;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.HexValue;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.TimeValue;
import net.sf.jsqlparser.expression.TimestampValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectItem;

/**
 * Reduces a SQL statement to a comparable template: existing table aliases are renumbered
 * positionally (t0, t1, ...) so Hibernate's own alias numbering drift between statements doesn't
 * break template equality, and literal constants are replaced with {@code ?} alongside existing
 * binds. Never throws — unparseable input falls back to a plain lowercase/trim/whitespace-collapse
 * of the raw text.
 */
public final class SqlNormalizer {

    private SqlNormalizer() {
    }

    public static String normalize(String sql) {
        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException | RuntimeException e) {
            return finalize(sql);
        }
        if (!(statement instanceof PlainSelect plainSelect)) {
            return finalize(sql);
        }

        Map<String, String> canonicalAliases = canonicalizeAliases(plainSelect);
        rewriteLiteralsAndAliasQualifiers(plainSelect, canonicalAliases);

        return finalize(plainSelect.toString());
    }

    private static Map<String, String> canonicalizeAliases(PlainSelect plainSelect) {
        Map<String, String> canonicalAliases = new LinkedHashMap<>();
        int index = 0;
        for (AliasedTable aliasedTable : AliasedTable.collect(plainSelect)) {
            Table table = aliasedTable.table();
            if (table.getAlias() != null) {
                String canonical = "t" + index++;
                canonicalAliases.put(table.getAlias().getName(), canonical);
                table.setAlias(new Alias(canonical));
            }
        }
        return canonicalAliases;
    }

    private static void rewriteLiteralsAndAliasQualifiers(PlainSelect plainSelect,
            Map<String, String> canonicalAliases) {
        LiteralAndAliasRewriter rewriter = new LiteralAndAliasRewriter(canonicalAliases);
        if (plainSelect.getWhere() != null) {
            plainSelect.getWhere().accept(rewriter, null);
        }
        if (plainSelect.getSelectItems() != null) {
            for (SelectItem<?> item : plainSelect.getSelectItems()) {
                if (item.getExpression() != null) {
                    item.getExpression().accept(rewriter, null);
                }
            }
        }
        if (plainSelect.getJoins() != null) {
            for (Join join : plainSelect.getJoins()) {
                for (Expression onExpression : join.getOnExpressions()) {
                    onExpression.accept(rewriter, null);
                }
            }
        }
    }

    private static String finalize(String sql) {
        return sql.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static boolean isLiteral(Expression expression) {
        return expression instanceof LongValue
                || expression instanceof DoubleValue
                || expression instanceof StringValue
                || expression instanceof DateValue
                || expression instanceof TimeValue
                || expression instanceof TimestampValue
                || expression instanceof HexValue;
    }

    private static void replaceLiteralSides(BinaryExpression comparison) {
        if (isLiteral(comparison.getLeftExpression())) {
            comparison.setLeftExpression(new JdbcParameter());
        }
        if (isLiteral(comparison.getRightExpression())) {
            comparison.setRightExpression(new JdbcParameter());
        }
    }

    /**
     * Rewrites Column table-qualifiers to their canonical alias and replaces literal constants
     * in comparison operators with {@code ?}. Relies on {@link ExpressionVisitorAdapter}'s
     * built-in recursive descent for everything else (functions, CASE, nested expressions, ...).
     */
    private static final class LiteralAndAliasRewriter extends ExpressionVisitorAdapter<Void> {

        private final Map<String, String> canonicalAliases;

        private LiteralAndAliasRewriter(Map<String, String> canonicalAliases) {
            this.canonicalAliases = canonicalAliases;
        }

        @Override
        public <S> Void visit(Column column, S context) {
            Table qualifier = column.getTable();
            if (qualifier != null && qualifier.getName() != null) {
                String canonical = canonicalAliases.get(qualifier.getName());
                if (canonical != null) {
                    qualifier.setName(canonical);
                }
            }
            return super.visit(column, context);
        }

        @Override
        public <S> Void visit(EqualsTo equalsTo, S context) {
            replaceLiteralSides(equalsTo);
            return super.visit(equalsTo, context);
        }

        @Override
        public <S> Void visit(NotEqualsTo notEqualsTo, S context) {
            replaceLiteralSides(notEqualsTo);
            return super.visit(notEqualsTo, context);
        }

        @Override
        public <S> Void visit(GreaterThan greaterThan, S context) {
            replaceLiteralSides(greaterThan);
            return super.visit(greaterThan, context);
        }

        @Override
        public <S> Void visit(GreaterThanEquals greaterThanEquals, S context) {
            replaceLiteralSides(greaterThanEquals);
            return super.visit(greaterThanEquals, context);
        }

        @Override
        public <S> Void visit(MinorThan minorThan, S context) {
            replaceLiteralSides(minorThan);
            return super.visit(minorThan, context);
        }

        @Override
        public <S> Void visit(MinorThanEquals minorThanEquals, S context) {
            replaceLiteralSides(minorThanEquals);
            return super.visit(minorThanEquals, context);
        }
    }
}