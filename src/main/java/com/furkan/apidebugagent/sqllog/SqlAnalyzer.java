package com.furkan.apidebugagent.sqllog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;

/**
 * Extracts the main table and parameterized WHERE equalities from a SELECT via JSqlParser's
 * AST — never string manipulation. Unparseable or non-SELECT input yields {@code parsed=false}
 * rather than throwing.
 */
public final class SqlAnalyzer {

    private SqlAnalyzer() {
    }

    public record ParsedSelect(String table, List<EqualityPredicate> parameterizedEqualities, boolean parsed) {

        private static ParsedSelect unparsed() {
            return new ParsedSelect(null, List.of(), false);
        }
    }

    public record EqualityPredicate(String table, String column) {
    }

    public static ParsedSelect parse(String sql) {
        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException | RuntimeException e) {
            return ParsedSelect.unparsed();
        }

        if (!(statement instanceof PlainSelect plainSelect)) {
            return ParsedSelect.unparsed();
        }
        if (!(plainSelect.getFromItem() instanceof Table mainTable) || mainTable.getName() == null) {
            return ParsedSelect.unparsed();
        }

        Map<String, String> aliasToTable = new LinkedHashMap<>();
        for (AliasedTable aliasedTable : AliasedTable.collect(plainSelect)) {
            aliasToTable.put(aliasedTable.alias(), aliasedTable.table().getName());
        }

        String mainTableName = mainTable.getName();
        List<EqualityPredicate> predicates =
                collectTopLevelEqualities(plainSelect.getWhere(), aliasToTable, mainTableName);

        return new ParsedSelect(mainTableName.toLowerCase(Locale.ROOT), predicates, true);
    }

    /**
     * Walks only the top-level AND chain of the WHERE clause (Parenthesis unwrapped
     * transparently) and collects {@code column = ?} equalities. Iterative — an explicit stack
     * instead of recursion. OR/IN/other operators are not descended into and yield no predicates
     * from that branch, but never throw.
     */
    private static List<EqualityPredicate> collectTopLevelEqualities(Expression where,
            Map<String, String> aliasToTable, String mainTableName) {
        List<EqualityPredicate> out = new ArrayList<>();
        if (where == null) {
            return out;
        }

        Deque<Expression> pending = new ArrayDeque<>();
        pending.push(where);
        while (!pending.isEmpty()) {
            Expression current = pending.pop();
            if (current == null) {
                continue;
            }
            if (current instanceof AndExpression and) {
                pending.push(and.getLeftExpression());
                pending.push(and.getRightExpression());
            } else if (current instanceof Parenthesis parenthesis) {
                pending.push(parenthesis.getExpression());
            } else if (current instanceof EqualsTo equalsTo
                    && equalsTo.getRightExpression() instanceof JdbcParameter
                    && equalsTo.getLeftExpression() instanceof Column column) {
                String resolvedTable = resolveTable(column, aliasToTable, mainTableName);
                if (resolvedTable != null && column.getColumnName() != null) {
                    out.add(new EqualityPredicate(resolvedTable.toLowerCase(Locale.ROOT),
                            column.getColumnName().toLowerCase(Locale.ROOT)));
                }
            }
            // OrExpression, InExpression and other operators are intentionally not descended
            // into — out of scope for the simple-equality-only detection rule.
        }
        return out;
    }

    private static String resolveTable(Column column, Map<String, String> aliasToTable, String mainTableName) {
        Table qualifier = column.getTable();
        if (qualifier == null || qualifier.getName() == null) {
            return aliasToTable.size() == 1 ? mainTableName : null;
        }
        return aliasToTable.get(qualifier.getName());
    }
}
