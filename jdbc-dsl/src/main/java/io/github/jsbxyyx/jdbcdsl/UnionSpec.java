package io.github.jsbxyyx.jdbcdsl;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

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
        UNION_ALL,
        /** {@code INTERSECT} — returns rows common to both queries, eliminates duplicates. */
        INTERSECT,
        /** {@code INTERSECT ALL} — returns rows common to both queries, preserving duplicates. */
        INTERSECT_ALL,
        /** {@code EXCEPT} — returns rows from the left query not in the right query, eliminates duplicates. */
        EXCEPT,
        /** {@code EXCEPT ALL} — returns rows from the left query not in the right query, preserving duplicates. */
        EXCEPT_ALL
    }

    /** A single branch in the union query. */
    public record Branch<R>(SelectSpec<?, R> spec, UnionType unionType) {}

    private final List<Branch<R>> branches;
    private final JSort<?> sort;

    private UnionSpec(List<Branch<R>> branches, JSort<?> sort) {
        this.branches = List.copyOf(branches);
        this.sort = sort != null ? sort : JSort.unsorted();
    }

    /** Starts a union with the given select as the first branch. */
    public static <R> Builder<R> of(SelectSpec<?, R> first) {
        return new Builder<>(first);
    }

    /** Returns all branches in order. The first branch's {@code unionType} is unused. */
    public List<Branch<R>> getBranches() { return branches; }

    /** Returns the DTO class from the first branch. */
    public Class<R> getDtoClass() { return branches.get(0).spec().getDtoClass(); }

    /** Returns the ORDER BY sort applied to the combined union result (may be unsorted). */
    public JSort<?> getSort() { return sort; }

    // ------------------------------------------------------------------ //

    /** Fluent builder for {@link UnionSpec}. */
    public static final class Builder<R> {

        private final List<Branch<R>> branches = new ArrayList<>();
        private JSort<?> sort = JSort.unsorted();

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

        /** Appends an {@code INTERSECT} (rows common to both, deduplicating) branch. */
        public Builder<R> intersect(SelectSpec<?, R> next) {
            branches.add(new Branch<>(next, UnionType.INTERSECT));
            return this;
        }

        /** Appends an {@code INTERSECT ALL} (rows common to both, preserving duplicates) branch. */
        public Builder<R> intersectAll(SelectSpec<?, R> next) {
            branches.add(new Branch<>(next, UnionType.INTERSECT_ALL));
            return this;
        }

        /** Appends an {@code EXCEPT} (rows in left but not right, deduplicating) branch. */
        public Builder<R> except(SelectSpec<?, R> next) {
            branches.add(new Branch<>(next, UnionType.EXCEPT));
            return this;
        }

        /** Appends an {@code EXCEPT ALL} (rows in left but not right, preserving duplicates) branch. */
        public Builder<R> exceptAll(SelectSpec<?, R> next) {
            branches.add(new Branch<>(next, UnionType.EXCEPT_ALL));
            return this;
        }

        /**
         * Specifies the ORDER BY clause applied to the combined union result.
         *
         * <p>The sort columns are referenced by their output alias (i.e. the property names
         * projected by each branch). Example:
         * <pre>{@code
         * UnionSpec.of(branch1).unionAll(branch2).orderBy(JSort.byAsc(TUser::getName)).build()
         * // → ... UNION ALL ... ORDER BY name ASC
         * }</pre>
         */
        public Builder<R> orderBy(JSort<?> sort) {
            this.sort = sort != null ? sort : JSort.unsorted();
            return this;
        }

        /** Builds the immutable {@link UnionSpec}. */
        public UnionSpec<R> build() {
            if (branches.size() < 2) {
                throw new IllegalStateException("UnionSpec requires at least two SELECT branches");
            }
            return new UnionSpec<>(branches, sort);
        }
    }
}
