package io.github.jsbxyyx.jpadsl;

import jakarta.persistence.metamodel.SingularAttribute;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collections;
import java.util.List;

/**
 * Immutable product object produced by {@link SelectBuilder#mapTo(Class)}.
 *
 * <p>Instances of this class are created exclusively by {@link SelectBuilder#mapTo(Class)}
 * and consumed by {@link JpaSelectExecutor}.
 *
 * @param <T> the root entity type
 * @param <R> the DTO/projection result type
 */
public final class SelectSpec<T, R> {

    private final Class<T> entityClass;
    private final Class<R> dtoClass;
    private final List<SingularAttribute<? super T, ?>> selectedAttrs;
    private final Specification<T> whereSpec;

    SelectSpec(Class<T> entityClass,
               Class<R> dtoClass,
               List<SingularAttribute<? super T, ?>> selectedAttrs,
               Specification<T> whereSpec) {
        if (dtoClass == null) {
            throw new IllegalArgumentException("dtoClass must not be null");
        }
        if (selectedAttrs == null || selectedAttrs.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one attribute must be selected via select(...)");
        }
        this.entityClass = entityClass;
        this.dtoClass = dtoClass;
        this.selectedAttrs = Collections.unmodifiableList(selectedAttrs);
        this.whereSpec = whereSpec;
    }

    /**
     * Returns the root entity class to query from.
     */
    public Class<T> getEntityClass() {
        return entityClass;
    }

    /**
     * Returns the DTO class to project results into via constructor expression.
     */
    public Class<R> getDtoClass() {
        return dtoClass;
    }

    /**
     * Returns an unmodifiable ordered list of attributes to select.
     * The order must match the DTO constructor parameter order.
     */
    public List<SingularAttribute<? super T, ?>> getSelectedAttrs() {
        return selectedAttrs;
    }

    /**
     * Returns the optional WHERE specification, or {@code null} if no filter is applied.
     */
    public Specification<T> getWhereSpec() {
        return whereSpec;
    }
}
