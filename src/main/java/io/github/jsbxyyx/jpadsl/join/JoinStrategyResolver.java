package io.github.jsbxyyx.jpadsl.join;

/**
 * Resolves the appropriate {@link JoinStrategy} implementation at runtime by probing whether
 * Hibernate 6+ is present on the classpath.
 *
 * <p>The resolution is performed once at class-load time and cached.  Two strategies are
 * supported:
 * <ol>
 *   <li><strong>Hibernate 6+ (default when available)</strong> — {@link HibernateJoinStrategy}:
 *       uses {@code JpaRoot.join(Class, JoinType)} to produce a true SQL {@code JOIN ... ON},
 *       with full LEFT / RIGHT JOIN semantics.</li>
 *   <li><strong>JPA standard fallback</strong> — {@link StandardJoinStrategy}: uses
 *       {@code CriteriaQuery.from(Class)} (cross join) and moves the ON condition to WHERE,
 *       effectively producing an INNER JOIN.</li>
 * </ol>
 *
 * <p>This class is transparent to callers — just call {@link #resolve()} to obtain the
 * appropriate strategy.
 */
public final class JoinStrategyResolver {

    private static final JoinStrategy STRATEGY = detect();

    private JoinStrategyResolver() {
    }

    private static JoinStrategy detect() {
        try {
            Class.forName("org.hibernate.query.criteria.JpaFrom");
            return new HibernateJoinStrategy();
        } catch (ClassNotFoundException e) {
            return new StandardJoinStrategy();
        }
    }

    /**
     * Returns the resolved {@link JoinStrategy} for the current runtime environment.
     *
     * @return {@link HibernateJoinStrategy} when Hibernate 6+ is on the classpath,
     *         {@link StandardJoinStrategy} otherwise
     */
    public static JoinStrategy resolve() {
        return STRATEGY;
    }
}
