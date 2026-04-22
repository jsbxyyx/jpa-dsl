package io.github.jsbxyyx.jdbcast.spring;

import java.util.List;

/**
 * Lightweight pagination result returned by {@link JdbcAstExecutor#queryPage}.
 *
 * <p>Contains the current-page rows and the total number of matching rows (without paging),
 * enough to construct any framework-specific page object (e.g. Spring Data's {@code PageImpl}).
 *
 * <pre>{@code
 * PageResult<TUser> page = executor.queryPage(
 *     SQL.from(u).select(u.star()).where(...).orderBy(...),
 *     TUser.class, 0, 20);
 *
 * // Convert to Spring Data Page if needed:
 * Page<TUser> springPage = new PageImpl<>(page.content(),
 *         PageRequest.of((int) page.pageNumber(), (int) page.pageSize()), page.total());
 * }</pre>
 *
 * @param <T> row type
 */
public record PageResult<T>(
        List<T> content,
        long    total,
        long    pageNumber,
        long    pageSize
) {
    /** Total number of pages. */
    public long totalPages() {
        return pageSize == 0 ? 1 : (total + pageSize - 1) / pageSize;
    }

    /** Whether this is the first page. */
    public boolean isFirst() { return pageNumber == 0; }

    /** Whether this is the last page. */
    public boolean isLast() { return pageNumber >= totalPages() - 1; }

    /** Whether there is a next page. */
    public boolean hasNext() { return pageNumber < totalPages() - 1; }
}
