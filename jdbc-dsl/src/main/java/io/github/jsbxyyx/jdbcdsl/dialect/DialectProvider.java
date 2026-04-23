package io.github.jsbxyyx.jdbcdsl.dialect;

/**
 * SPI for plugging in custom {@link Dialect} implementations.
 *
 * <p>Implement this interface to support any database not covered by the built-in dialects,
 * then register the implementation via the standard Java {@link java.util.ServiceLoader} mechanism:
 *
 * <ol>
 *   <li>Create your dialect class, e.g. {@code MyCustomDialect implements Dialect}.</li>
 *   <li>Create a provider class that implements {@code DialectProvider}.</li>
 *   <li>Register it in {@code META-INF/services/io.github.jsbxyyx.jdbcdsl.dialect.DialectProvider}
 *       with the fully-qualified class name of your provider.</li>
 * </ol>
 *
 * <p>Example provider for a hypothetical "FooDB" database:
 * <pre>{@code
 * public class FooDialectProvider implements DialectProvider {
 *
 *     @Override
 *     public boolean supports(String databaseProductName) {
 *         return databaseProductName != null
 *                 && databaseProductName.toLowerCase().contains("foodb");
 *     }
 *
 *     @Override
 *     public Dialect create() {
 *         return new FooDialect();
 *     }
 * }
 * }</pre>
 *
 * <p>User-registered providers are always consulted <em>before</em> the built-in detection logic,
 * so they can also override built-in dialects for a specific database product.
 *
 * @see Dialect
 * @see DialectDetector
 */
public interface DialectProvider {

    /**
     * Returns {@code true} if this provider can supply a {@link Dialect} for the given database
     * product name (as reported by {@link java.sql.DatabaseMetaData#getDatabaseProductName()}).
     *
     * @param databaseProductName the product name string from JDBC metadata; may be {@code null}
     * @return {@code true} if this provider handles the given product name
     */
    boolean supports(String databaseProductName);

    /**
     * Creates and returns a new {@link Dialect} instance for the database.
     *
     * <p>This method is only called when {@link #supports} returns {@code true}.
     *
     * @return a non-null {@link Dialect}
     */
    Dialect create();
}
