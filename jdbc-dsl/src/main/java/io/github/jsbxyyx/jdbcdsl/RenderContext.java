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
    private int rawParamIdx;

    /**
     * Creates a new render context with an empty parameter map.
     */
    public RenderContext() {
        this.params = new LinkedHashMap<>();
        this.paramIdx = 0;
        this.rawParamIdx = 0;
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
     * Rewrites named parameters in a raw SQL fragment to names unique to that fragment.
     * Markers inside quoted strings, quoted identifiers, and SQL comments are left untouched.
     */
    public String mergeRawParams(String sql, Map<String, Object> rawParams) {
        Map<String, String> renamed = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rawParams.entrySet()) {
            String name;
            do {
                name = "raw" + (++rawParamIdx) + "_" + entry.getKey();
            } while (params.containsKey(name));
            renamed.put(entry.getKey(), name);
            params.put(name, entry.getValue());
        }
        return rewriteNamedParameters(sql, renamed);
    }

    private static String rewriteNamedParameters(String sql, Map<String, String> renamed) {
        StringBuilder result = new StringBuilder(sql.length());
        int i = 0;
        char quote = 0;
        while (i < sql.length()) {
            char ch = sql.charAt(i);
            if (quote != 0) {
                result.append(ch);
                if (ch == quote) {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                        result.append(sql.charAt(++i));
                    } else {
                        quote = 0;
                    }
                }
                i++;
                continue;
            }
            if (ch == 39 || ch == 34 || ch == 96) {
                quote = ch;
                result.append(ch);
                i++;
                continue;
            }
            if (ch == '[') {
                int end = i + 1;
                while (end < sql.length()) {
                    if (sql.charAt(end) == ']') {
                        if (end + 1 < sql.length() && sql.charAt(end + 1) == ']') {
                            end += 2;
                            continue;
                        }
                        end++;
                        break;
                    }
                    end++;
                }
                result.append(sql, i, end);
                i = end;
                continue;
            }
            if (ch == '$') {
                String delimiter = dollarQuoteDelimiterAt(sql, i);
                if (delimiter != null) {
                    int end = sql.indexOf(delimiter, i + delimiter.length());
                    if (end < 0) {
                        result.append(sql, i, sql.length());
                        break;
                    }
                    end += delimiter.length();
                    result.append(sql, i, end);
                    i = end;
                    continue;
                }
            }
            if (ch == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                int end = i + 2;
                while (end < sql.length() && sql.charAt(end) != '\n' && sql.charAt(end) != '\r') {
                    end++;
                }
                result.append(sql, i, end);
                i = end;
                continue;
            }
            if (ch == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                if (end < 0) {
                    result.append(sql, i, sql.length());
                    break;
                }
                result.append(sql, i, end + 2);
                i = end + 2;
                continue;
            }
            if (ch == ':'
                    && (i == 0 || sql.charAt(i - 1) != ':')
                    && i + 1 < sql.length()
                    && sql.charAt(i + 1) != ':'
                    && isParameterStart(sql.charAt(i + 1))) {
                int end = i + 2;
                while (end < sql.length() && isParameterPart(sql.charAt(end))) {
                    end++;
                }
                String original = sql.substring(i + 1, end);
                String replacement = renamed.get(original);
                result.append(replacement == null ? sql.substring(i, end) : ":" + replacement);
                i = end;
                continue;
            }
            result.append(ch);
            i++;
        }
        return result.toString();
    }

    private static String dollarQuoteDelimiterAt(String sql, int start) {
        int end = start + 1;
        if (end < sql.length()
                && sql.charAt(end) != '$'
                && !Character.isLetter(sql.charAt(end))
                && sql.charAt(end) != '_') {
            return null;
        }
        while (end < sql.length() && (Character.isLetterOrDigit(sql.charAt(end)) || sql.charAt(end) == '_')) {
            end++;
        }
        if (end < sql.length() && sql.charAt(end) == '$') {
            return sql.substring(start, end + 1);
        }
        return null;
    }

    private static boolean isParameterStart(char ch) {
        return Character.isLetter(ch) || ch == '_';
    }

    private static boolean isParameterPart(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_';
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
