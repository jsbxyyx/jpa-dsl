package io.github.jsbxyyx.jdbcast.renderer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Accumulates named parameters during SQL rendering.
 *
 * <p>Each call to {@link #addParam(Object)} generates a unique parameter name
 * (e.g., {@code :p1}, {@code :p2}) and returns the placeholder string.
 */
public final class RenderContext {

    private final Map<String, Object> params  = new LinkedHashMap<>();
    private final AtomicInteger       counter = new AtomicInteger(0);

    /**
     * Registers a value as a named parameter and returns its placeholder (e.g., {@code :p3}).
     */
    public String addParam(Object value) {
        String name = "p" + counter.incrementAndGet();
        params.put(name, value);
        return ":" + name;
    }

    /**
     * Merges externally-supplied params (e.g., from {@link io.github.jsbxyyx.jdbcast.expr.RawExpr})
     * into this context. Duplicate keys are overwritten.
     */
    public void mergeParams(Map<String, Object> external) {
        params.putAll(external);
    }

    public Map<String, Object> params() {
        return Collections.unmodifiableMap(params);
    }
}
