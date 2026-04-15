package io.github.jsbxyyx.jdbcdsl;

/**
 * {@link NamingStrategy} that converts {@code camelCase} Java names to {@code snake_case}
 * database identifiers.
 *
 * <p>Conversion rules:
 * <ul>
 *   <li>Each uppercase letter is preceded by an underscore and lowercased.</li>
 *   <li>The first character is never preceded by an underscore.</li>
 * </ul>
 *
 * <p>Examples:
 * <pre>
 *   userId      → user_id
 *   orderNo     → order_no
 *   createdAt   → created_at
 *   UserInfo    → user_info       (class → table)
 *   TProduct    → t_product       (class → table)
 * </pre>
 *
 * <p>Explicit {@link jakarta.persistence.Column} / {@link jakarta.persistence.Table}
 * annotations always take precedence over this strategy.
 *
 * <p>Enable via {@code application.properties}:
 * <pre>
 *   jdbcdsl.naming-strategy=snake_case
 * </pre>
 * or by registering a {@code NamingStrategy} Spring bean.
 */
public final class SnakeCaseNamingStrategy implements NamingStrategy {

    public static final SnakeCaseNamingStrategy INSTANCE = new SnakeCaseNamingStrategy();

    private SnakeCaseNamingStrategy() {
    }

    @Override
    public String propertyToColumn(String propertyName) {
        return toSnakeCase(propertyName);
    }

    @Override
    public String classToTable(String className) {
        return toSnakeCase(className);
    }

    static String toSnakeCase(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        StringBuilder sb = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
