package io.github.jsbxyyx.jdbcdsl;

import java.util.ArrayList;
import java.util.List;

/**
 * Specification for a {@code UNION} or {@code UNION ALL} query composed of multiple
 * {@link SelectSpec} branches.
 *
 * <p>All branches must project to the same result type {@code R}.
 *
 * <p>Usage example:
 * <pre>{@code
 * SelectSpec<User, NameDto> active = SelectBuilder.from(User.class)
 *     .select(User::getName)
 *     .where(w -> w.eq(User::getStatus, 1))
 *     .mapTo(NameDto.class);
 *
 * SelectSpec<Admin, NameDto> admins = SelectBuilder.from(Admin.class)
 *     .select(Admin::getName)
 *     .mapTo(NameDto.class);
 *
 * UnionSpec<NameDto> union = UnionSpec.of(active).unionAll(admins);
 * List<NameDto> results = executor.union(union);
 * }</pre>
 *
 * @param <R> the common result (DTO) type for all branches
 */
public final class UnionSpec<R> {

    /** Marker for the operator between two adjacent SELECT branches. */
    public enum UnionType {
        /** {@code UNION} — eliminates duplicate rows. */
        UNION,
        /** {@code UNION ALL} — preserves all rows including duplicates. */
        UNION_ALL
    }

    /** A single branch in the union query. */
    public record Branch<R>(SelectSpec<?, R> spec, UnionType unionType) {}

    private final List<Branch<R>> branches;

    private UnionSpec(List<Branch<R>> branches) {
        this.branches = List.copyOf(branches);
    }

    /** Starts a union with the given select as the first branch. */
    public static <R> Builder<R> of(SelectSpec<?, R> first) {
        return new Builder<>(first);
    }

    /** Returns all branches in order. The first branch's {@code unionType} is unused. */
    public List<Branch<R>> getBranches() { return branches; }

    /** Returns the DTO class from the first branch. */
    public Class<R> getDtoClass() { return branches.get(0).spec().getDtoClass(); }

    // ------------------------------------------------------------------ //

    /** Fluent builder for {@link UnionSpec}. */
    public static final class Builder<R> {

        private final List<Branch<R>> branches = new ArrayList<>();

        private Builder(SelectSpec<?, R> first) {
            // The first branch's unionType is a placeholder (never rendered).
            branches.add(new Branch<>(first, UnionType.UNION));
        }

        /** Appends a {@code UNION} (deduplicating) branch. */
        public Builder<R> union(SelectSpec<?, R> next) {
            branches.add(new Branch<>(next, UnionType.UNION));
            return this;
        }

        /** Appends a {@code UNION ALL} (preserving duplicates) branch. */
        public Builder<R> unionAll(SelectSpec<?, R> next) {
            branches.add(new Branch<>(next, UnionType.UNION_ALL));
            return this;
        }

        /** Builds the immutable {@link UnionSpec}. */
        public UnionSpec<R> build() {
            if (branches.size() < 2) {
                throw new IllegalStateException("UnionSpec requires at least two SELECT branches");
            }
            return new UnionSpec<>(branches);
        }
    }
}
