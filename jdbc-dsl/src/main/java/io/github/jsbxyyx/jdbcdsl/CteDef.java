package io.github.jsbxyyx.jdbcdsl;

/**
 * A single Common Table Expression (CTE) definition: a name and its body query.
 *
 * <p>Used with {@link SelectBuilder#withCte(String, SelectSpec)} to build a {@code WITH} clause:
 * <pre>{@code
 * SelectSpec<TUser, UserDto> spec = SelectBuilder.fromCte("active_users", TUser.class)
 *     .withCte("active_users", SelectBuilder.from(TUser.class)
 *         .where(w -> w.eq(TUser::getStatus, "ACTIVE"))
 *         .mapToEntity())
 *     .select(TUser::getId, TUser::getUsername)
 *     .mapTo(UserDto.class);
 * // → WITH active_users AS (SELECT … FROM t_user t WHERE t.status = :p1)
 * //   SELECT t.id AS id, t.username AS username FROM active_users t
 * }</pre>
 *
 * @param name the CTE name used in the WITH clause and referenced in FROM / JOIN clauses
 * @param body the SELECT spec for the CTE body (ORDER BY is omitted in the CTE body)
 */
public record CteDef(String name, SelectSpec<?, ?> body) {
}
