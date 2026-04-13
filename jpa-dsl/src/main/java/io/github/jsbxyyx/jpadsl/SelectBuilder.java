package io.github.jsbxyyx.jpadsl;

import jakarta.persistence.metamodel.SingularAttribute;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fluent builder for constructing DTO projection queries using type-safe
 * JPA Static Metamodel attribute references.
 *
 * <p>Supports single-table (root) field projection with optional WHERE filtering
 * and produces a {@link SelectSpec} that can be executed via
 * {@link JpaSelectExecutor#select(SelectSpec)} or
 * {@link JpaSelectExecutor#selectPage(SelectSpec, org.springframework.data.domain.Pageable)}.
 *
 * <p>Usage example:
 * <pre>{@code
 * record UserDto(Long id, String username, String nickname) {}
 *
 * SelectSpec<User, UserDto> spec = SelectBuilder.from(User.class)
 *     .select(User_.id, User_.username, User_.nickname)
 *     .where(SpecificationBuilder.<User>builder()
 *         .like(User_.username, keyword)
 *         .build())
 *     .mapTo(UserDto.class);
 *
 * List<UserDto> list = userRepository.select(spec);
 * Page<UserDto> page = userRepository.selectPage(spec, PageRequest.of(0, 10));
 * }</pre>
 *
 * <p>Only root single-table attributes ({@link SingularAttribute}) are supported.
 * Join-based projections are out of scope; use {@code @Query} for those cases.
 *
 * @param <T> the root entity type
 */
public class SelectBuilder<T> {

    private final Class<T> entityClass;
    private final List<SingularAttribute<? super T, ?>> selectedAttrs = new ArrayList<>();
    private Specification<T> whereSpec;

    private SelectBuilder(Class<T> entityClass) {
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        this.entityClass = entityClass;
    }

    /**
     * Creates a new {@code SelectBuilder} for the given entity class.
     *
     * @param entityClass the JPA entity class to query from
     * @param <T>         the entity type
     * @return a new builder instance
     */
    public static <T> SelectBuilder<T> from(Class<T> entityClass) {
        return new SelectBuilder<>(entityClass);
    }

    /**
     * Specifies the root entity attributes to project.
     *
     * <p>The attribute order must match the DTO constructor parameter order.
     * Only {@link SingularAttribute} (i.e. scalar, non-collection fields) are accepted.
     *
     * @param attrs the attributes to select; must not be null or empty
     * @return this builder for chaining
     */
    @SafeVarargs
    public final SelectBuilder<T> select(SingularAttribute<? super T, ?>... attrs) {
        if (attrs == null || attrs.length == 0) {
            throw new IllegalArgumentException("At least one attribute must be provided to select(...)");
        }
        this.selectedAttrs.addAll(Arrays.asList(attrs));
        return this;
    }

    /**
     * Applies a WHERE filter using a {@link Specification}.
     *
     * <p>The specification is typically built with {@link SpecificationBuilder}:
     * <pre>{@code
     * .where(SpecificationBuilder.<User>builder()
     *     .like(User_.username, keyword, keyword != null)
     *     .build())
     * }</pre>
     *
     * <p>Calling this method with {@code null} is allowed and results in no WHERE clause.
     *
     * @param spec the where specification; may be {@code null}
     * @return this builder for chaining
     */
    public SelectBuilder<T> where(Specification<T> spec) {
        this.whereSpec = spec;
        return this;
    }

    /**
     * Builds the final {@link SelectSpec} targeting the given DTO class.
     *
     * <p>The DTO class must have a constructor whose parameter types match the
     * selected attribute types in the same order (constructor expression projection).
     * Records and regular classes with a matching constructor are both supported.
     *
     * @param dtoClass the DTO class to project results into; must not be {@code null}
     * @param <R>      the DTO result type
     * @return an immutable {@link SelectSpec} ready for execution
     * @throws IllegalArgumentException if no attributes were selected or dtoClass is null
     */
    public <R> SelectSpec<T, R> mapTo(Class<R> dtoClass) {
        return new SelectSpec<>(entityClass, dtoClass, new ArrayList<>(selectedAttrs), whereSpec);
    }
}
