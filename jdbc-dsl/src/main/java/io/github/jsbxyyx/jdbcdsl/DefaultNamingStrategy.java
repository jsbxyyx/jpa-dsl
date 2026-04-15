package io.github.jsbxyyx.jdbcdsl;

/**
 * Identity {@link NamingStrategy}: returns Java names unchanged.
 *
 * <p>This is the default strategy. A property named {@code userId} maps to column
 * {@code userId}, and a class named {@code UserInfo} maps to table {@code UserInfo}.
 * Explicit {@link jakarta.persistence.Column} / {@link jakarta.persistence.Table}
 * annotations always take precedence regardless of the active strategy.
 */
public final class DefaultNamingStrategy implements NamingStrategy {

    public static final DefaultNamingStrategy INSTANCE = new DefaultNamingStrategy();

    private DefaultNamingStrategy() {
    }

    @Override
    public String propertyToColumn(String propertyName) {
        return propertyName;
    }

    @Override
    public String classToTable(String className) {
        return className;
    }
}
