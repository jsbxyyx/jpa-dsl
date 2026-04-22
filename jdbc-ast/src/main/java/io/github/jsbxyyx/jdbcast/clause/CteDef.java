package io.github.jsbxyyx.jdbcast.clause;

import io.github.jsbxyyx.jdbcast.stmt.SelectStatement;

/** A Common Table Expression definition: {@code name AS (query)}. */
public record CteDef(String name, SelectStatement query) {

    public static CteDef of(String name, SelectStatement query) {
        return new CteDef(name, query);
    }
}
