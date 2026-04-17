package io.github.jsbxyyx.jdbcdsl;

/**
 * Typed wrapper that marks a {@link SelectSpec} as a scalar subquery — one that returns
 * exactly one row and one column.
 *
 * <p>Use {@link #scalar(SelectSpec)} (static import recommended) to wrap the subquery
 * before passing it to a {@link WhereBuilder} comparison method:
 *
 * <pre>{@code
 * import static io.github.jsbxyyx.jdbcdsl.Scalar.scalar;
 *
 * .where(w -> w.gt(TUser::getAge, scalar(
 *     SelectBuilder.from(TUser.class)
 *         .select(avg(TUser::getAge).as("avgAge"))
 *         .mapToEntity())))
 * }</pre>
 *
 * <p>The wrapper eliminates the overload ambiguity that would otherwise arise between
 * typed value comparisons (e.g. {@code eq(SFunction<T,V>, V)}) and scalar subquery
 * comparisons when both share the same erased first-argument type.
 *
 * @param <V> the expected result type of the scalar subquery (informational; not enforced at runtime)
 */
public final class Scalar<V> {

    private final SelectSpec<?, ?> spec;

    private Scalar(SelectSpec<?, ?> spec) {
        this.spec = spec;
    }

    /**
     * Wraps {@code spec} as a scalar subquery.
     *
     * <p>Typically used via a static import:
     * <pre>{@code w.gt(TUser::getAge, scalar(inner)) }</pre>
     */
    public static <V> Scalar<V> scalar(SelectSpec<?, ?> spec) {
        return new Scalar<>(spec);
    }

    /** Returns the underlying {@link SelectSpec}. For internal DSL use only. */
    SelectSpec<?, ?> getSpec() {
        return spec;
    }
}
