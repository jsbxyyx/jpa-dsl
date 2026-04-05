package io.github.jsbxyyx.jdbcdsl;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Type-safe pagination specification for the jdbc-dsl.
 *
 * <p>This class does <em>not</em> accept Spring {@link Pageable} as input.
 * Use {@link #toSpringPageable()} to convert to Spring's {@code Pageable} for output only.
 *
 * @param <T> the entity type (used for type-safe sort references)
 */
public final class JPageable<T> {

    private final int page;
    private final int size;
    private final JSort<T> sort;

    private JPageable(int page, int size, JSort<T> sort) {
        if (page < 0) throw new IllegalArgumentException("Page index must not be negative");
        if (size < 1) throw new IllegalArgumentException("Page size must be positive");
        this.page = page;
        this.size = size;
        this.sort = sort;
    }

    /** Creates a pageable with no sort. Page index is 0-based. */
    public static <T> JPageable<T> of(int page, int size) {
        return new JPageable<>(page, size, JSort.unsorted());
    }

    /** Creates a pageable with the given sort. Page index is 0-based. */
    public static <T> JPageable<T> of(int page, int size, JSort<T> sort) {
        return new JPageable<>(page, size, sort != null ? sort : JSort.unsorted());
    }

    /** 0-based page index. */
    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    /** Zero-based row offset. */
    public long offset() {
        return (long) page * size;
    }

    public JSort<T> getSort() {
        return sort;
    }

    /**
     * Exports this pageable to a Spring Data {@link Pageable} (output adapter only).
     * Does not accept Spring Pageable as input.
     */
    public Pageable toSpringPageable() {
        return PageRequest.of(page, size, sort.toSpringSort());
    }
}
