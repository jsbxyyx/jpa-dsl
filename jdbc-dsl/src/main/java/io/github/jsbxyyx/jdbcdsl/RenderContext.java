package io.github.jsbxyyx.jdbcdsl;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rendering context that holds the parameter map and counter for SQL generation.
 *
 * <p>This class reduces object allocation during SQL rendering by reusing a single
 * context instance instead of passing separate {@code Map} and {@code AtomicInteger}
 * parameters through the call stack.
 *
 * <p>Thread-safety: This class is NOT thread-safe. Each rendering operation should
 * use its own {@code RenderContext} instance.
 */
public final class RenderContext {

    private final Map<String, Object> params;
    private int paramIdx;

    /**
     * Creates a new render context with an empty parameter map.
     */
    public RenderContext() {
        this.params = new LinkedHashMap<>();
        this.paramIdx = 0;
    }

    /**
     * Returns the parameter map containing all named parameters and their values.
     *
     * @return the parameter map (never {@code null})
     */
    public Map<String, Object> getParams() {
        return params;
    }

    /**
     * Generates the next unique parameter name and stores the value in the parameter map.
     *
     * <p>Parameter names follow the pattern {@code p1, p2, p3, ...}.
     *
     * @param value the parameter value to store
     * @return the generated parameter name (without the {@code :} prefix)
     */
    public String nextParam(Object value) {
        String name = "p" + (++paramIdx);
        params.put(name, value);
        return name;
    }

    /**
     * Returns the current parameter index value (number of parameters generated so far).
     *
     * @return the parameter index
     */
    public int getParamIdx() {
        return paramIdx;
    }
}
