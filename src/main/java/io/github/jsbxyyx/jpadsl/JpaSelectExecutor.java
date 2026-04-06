package io.github.jsbxyyx.jpadsl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Repository mix-in interface that adds type-safe DTO projection query capability
 * to any Spring Data JPA repository.
 *
 * <p>Extend this interface alongside {@code JpaRepository} and
 * {@code JpaSpecificationExecutor} to gain {@link #select} and {@link #selectPage}
 * methods without ever touching {@code EntityManager} directly:
 *
 * <pre>{@code
 * @Repository
 * public interface UserRepository extends JpaRepository<User, Long>,
 *         JpaSpecificationExecutor<User>,
 *         JpaSelectExecutor<User> {
 * }
 * }</pre>
 *
 * <p>Usage in a service:
 * <pre>{@code
 * record UserDto(Long id, String username, String nickname) {}
 *
 * SelectSpec<User, UserDto> spec = SelectBuilder.from(User.class)
 *     .select(User_.id, User_.username, User_.nickname)
 *     .where(SpecificationBuilder.<User>builder()
 *         .like(User_.username, keyword, keyword != null)
 *         .build())
 *     .mapTo(UserDto.class);
 *
 * List<UserDto> list = userRepository.select(spec);
 * Page<UserDto> page = userRepository.selectPage(spec, PageRequest.of(0, 10, Sort.by("username")));
 * }</pre>
 *
 * <p><strong>Limitations:</strong> only root single-table fields are supported for
 * projection. For complex join-based projections, use {@code @Query}.
 *
 * @param <T> the root entity type
 * @see SelectBuilder
 * @see SelectSpec
 * @see JpaSelectExecutorImpl
 */
public interface JpaSelectExecutor<T> {

    /**
     * Executes a DTO projection query described by the given {@link SelectSpec}
     * and returns all matching results as a list.
     *
     * @param spec the select specification (selected fields + optional WHERE)
     * @param <R>  the DTO result type
     * @return a list of projected DTO instances; never {@code null}
     * @throws IllegalArgumentException if the spec has no selected attributes or no DTO class
     */
    <R> List<R> select(SelectSpec<T, R> spec);

    /**
     * Executes a paginated DTO projection query described by the given {@link SelectSpec}.
     *
     * <p>Internally executes two queries: one for the data (with limit/offset and optional
     * sort from {@code pageable}) and one count query for the total number of matching rows.
     *
     * <p>Sorting from {@code pageable.getSort()} is applied to root entity fields only.
     * If a sort property does not exist on the entity, the JPA provider will throw an
     * exception at runtime.
     *
     * @param spec     the select specification (selected fields + optional WHERE)
     * @param pageable pagination and sort information; must not be {@code null}
     * @param <R>      the DTO result type
     * @return a {@link Page} containing the projected DTO instances and total count
     * @throws IllegalArgumentException if the spec has no selected attributes or no DTO class
     */
    <R> Page<R> selectPage(SelectSpec<T, R> spec, Pageable pageable);
}
