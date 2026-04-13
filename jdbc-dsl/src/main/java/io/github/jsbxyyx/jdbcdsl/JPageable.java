package io.github.jsbxyyx.jdbcdsl;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Type-safe pagination specification for the jdbc-dsl, mirroring Spring's {@code PageRequest} API.
 *
 * <p>Uses {@link JSort} (backed by {@link SFunction} method references) instead of Spring's
 * {@code Sort}. This class does <em>not</em> accept Spring {@link Pageable} as input.
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
        this.sort = sort != null ? sort : JSort.unsorted();
    }

    // ------------------------------------------------------------------ //
    //  Static factories (mirror PageRequest.of / PageRequest.ofSize)
    // ------------------------------------------------------------------ //

    /** Creates a pageable with no sort. Page index is 0-based (mirrors {@code PageRequest.of(page, size)}). */
    public static <T> JPageable<T> of(int page, int size) {
        return new JPageable<>(page, size, JSort.unsorted());
    }

    /** Creates a pageable with the given sort (mirrors {@code PageRequest.of(page, size, Sort)}). */
    public static <T> JPageable<T> of(int page, int size, JSort<T> sort) {
        return new JPageable<>(page, size, sort);
    }

    /**
     * Creates a pageable with the given direction and properties
     * (mirrors {@code PageRequest.of(page, size, Direction, String...)}).
     */
    @SafeVarargs
    public static <T> JPageable<T> of(int page, int size,
                                      JOrder.Direction direction, SFunction<T, ?>... props) {
        return new JPageable<>(page, size, JSort.by(direction, props));
    }

    /**
     * Creates a pageable for the first page (page 0) with the given size and no sort
     * (mirrors {@code PageRequest.ofSize(int)}).
     */
    public static <T> JPageable<T> ofSize(int size) {
        return new JPageable<>(0, size, JSort.unsorted());
    }

    // ------------------------------------------------------------------ //
    //  Accessors (Spring-compatible names + original names)
    // ------------------------------------------------------------------ //

    /** 0-based page index (original name). */
    public int getPage() {
        return page;
    }

    /** 0-based page index (mirrors {@code Pageable.getPageNumber()}). */
    public int getPageNumber() {
        return page;
    }

    /** Page size (original name). */
    public int getSize() {
        return size;
    }

    /** Page size (mirrors {@code Pageable.getPageSize()}). */
    public int getPageSize() {
        return size;
    }

    /** Zero-based row offset (original name). */
    public long offset() {
        return (long) page * size;
    }

    /** Zero-based row offset (mirrors {@code Pageable.getOffset()}). */
    public long getOffset() {
        return offset();
    }

    /** Returns the sort specification. */
    public JSort<T> getSort() {
        return sort;
    }

    /**
     * Returns the sort or the given default if unsorted
     * (mirrors {@code Pageable.getSortOr(Sort)}).
     */
    public JSort<T> getSortOr(JSort<T> defaultSort) {
        return sort.isUnsorted() ? defaultSort : sort;
    }

    // ------------------------------------------------------------------ //
    //  Navigation methods (mirror PageRequest / AbstractPageRequest)
    // ------------------------------------------------------------------ //

    /** Returns a new pageable for the next page (mirrors {@code PageRequest.next()}). */
    public JPageable<T> next() {
        return new JPageable<>(page + 1, size, sort);
    }

    /**
     * Returns a new pageable for the previous page, or the first page if already at page 0
     * (mirrors {@code AbstractPageRequest.previousOrFirst()}).
     */
    public JPageable<T> previousOrFirst() {
        return hasPrevious() ? previous() : first();
    }

    /**
     * Returns a new pageable for the previous page
     * (mirrors {@code PageRequest.previous()}).
     * Throws {@link IllegalStateException} if already at the first page; use
     * {@link #previousOrFirst()} for safe navigation.
     */
    public JPageable<T> previous() {
        if (!hasPrevious()) {
            throw new IllegalStateException("Already at the first page — use previousOrFirst() for safe navigation");
        }
        return new JPageable<>(page - 1, size, sort);
    }

    /**
     * Returns a new pageable for the first page (page 0) with the same size and sort
     * (mirrors {@code PageRequest.first()}).
     */
    public JPageable<T> first() {
        return page == 0 ? this : new JPageable<>(0, size, sort);
    }

    /**
     * Returns a new pageable with the given page number, keeping size and sort
     * (mirrors {@code PageRequest.withPage(int)}).
     */
    public JPageable<T> withPage(int pageNumber) {
        return pageNumber == page ? this : new JPageable<>(pageNumber, size, sort);
    }

    /**
     * Returns a new pageable with the given sort, keeping page and size
     * (mirrors {@code PageRequest.withSort(Sort)}).
     */
    public JPageable<T> withSort(JSort<T> sort) {
        return new JPageable<>(page, size, sort);
    }

    /**
     * Returns a new pageable with a sort derived from the given direction and properties,
     * keeping page and size (mirrors {@code PageRequest.withSort(Direction, String...)}).
     */
    @SafeVarargs
    public final JPageable<T> withSort(JOrder.Direction direction, SFunction<T, ?>... props) {
        return new JPageable<>(page, size, JSort.by(direction, props));
    }

    /** Returns {@code true} if there is a previous page (page &gt; 0). */
    public boolean hasPrevious() {
        return page > 0;
    }

    /** Always returns {@code true} — {@code JPageable} is always paged. */
    public boolean isPaged() {
        return true;
    }

    /** Always returns {@code false} — {@code JPageable} is always paged. */
    public boolean isUnpaged() {
        return false;
    }

    // ------------------------------------------------------------------ //
    //  Output adapter
    // ------------------------------------------------------------------ //

    /**
     * Exports this pageable to a Spring Data {@link Pageable} (output adapter only).
     * Does not accept Spring Pageable as input.
     */
    public Pageable toSpringPageable() {
        return PageRequest.of(page, size, sort.toSpringSort());
    }

    @Override
    public String toString() {
        return "JPageable{page=" + page + ", size=" + size + ", sort=" + sort + '}';
    }
}
