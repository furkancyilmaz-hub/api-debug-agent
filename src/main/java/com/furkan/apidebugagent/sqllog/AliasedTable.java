package com.furkan.apidebugagent.sqllog;

import java.util.ArrayList;
import java.util.List;

import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;

/**
 * A FROM/JOIN table paired with the identifier a query uses to refer to it —
 * its alias if it has one, otherwise its own table name.
 */
record AliasedTable(String alias, Table table) {

    static List<AliasedTable> collect(PlainSelect plainSelect) {
        List<AliasedTable> result = new ArrayList<>();
        if (plainSelect.getFromItem() instanceof Table mainTable) {
            result.add(new AliasedTable(aliasOrName(mainTable), mainTable));
        }
        if (plainSelect.getJoins() != null) {
            for (Join join : plainSelect.getJoins()) {
                if (join.getFromItem() instanceof Table joinTable) {
                    result.add(new AliasedTable(aliasOrName(joinTable), joinTable));
                }
            }
        }
        return result;
    }

    private static String aliasOrName(Table table) {
        return table.getAlias() != null ? table.getAlias().getName() : table.getName();
    }
}