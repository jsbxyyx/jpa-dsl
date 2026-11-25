package io.github.jsbxyyx.jdbcdsl.bench;

import java.util.Map;

/** Builds mapper SQL with the isolated table names supplied by each benchmark instance. */
public final class BenchmarkMapperSql {
    private BenchmarkMapperSql() {}

    public static String selectRows(Map<String, Object> parameters) {
        return "SELECT id, name, amount FROM " + requireTable(parameters.get("table"))
                + " WHERE id <= #{max} ORDER BY id";
    }

    public static String insertWrite(Map<String, Object> parameters) {
        return "INSERT INTO " + requireTable(parameters.get("table"))
                + " (id, name, amount) VALUES (#{row.id}, #{row.name}, #{row.amount})";
    }

    private static String requireTable(Object table) {
        if (!(table instanceof String name) || !name.matches("[A-Za-z][A-Za-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("A valid benchmark table name is required");
        }
        return name;
    }
}
