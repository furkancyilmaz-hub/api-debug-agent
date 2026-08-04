package com.furkan.apidebugagent.schema;

public record ForeignKey(String childTable, String childColumn, String parentTable, String parentColumn) {
}
