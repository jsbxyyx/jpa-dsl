package io.github.jsbxyyx.jdbcdsl.expr;

import io.github.jsbxyyx.jdbcdsl.JOrder;
import io.github.jsbxyyx.jdbcdsl.PropertyRefResolver;
import io.github.jsbxyyx.jdbcdsl.SFunction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * A SQL window function expression: {@code FUNC(...) OVER ([PARTITION BY ...] [ORDER BY ...])}.
 *
 * <p>Window functions compute a value across a set of rows related to the current row
 * without collapsing them into groups (unlike aggregate functions with GROUP BY).
 *
 * <p>Create instances via {@link AggregateExpression#over(Consumer)} or
 * {@link FunctionExpression#over(Consumer)} from a window-capable expression:
 * <pre>{@code
 * import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.*;
 *
 * // ROW_NUMBER() OVER (PARTITION BY t.status ORDER BY t.age DESC)
 * rowNumber().over(w -> w.partitionBy(TUser::getStatus).orderBy(JOrder.desc(TUser::getAge))).as("rn")
 *
 * // SUM(t.amount) OVER (PARTITION BY t.user_id ORDER BY t.id ASC)
 * sum(TOrder::getAmount).over(w -> w.partitionBy(TOrder::getUserId).orderBy(JOrder.asc(TOrder::getId))).as("cumAmt")
 *
 * // LAG(t.age, 1) OVER (ORDER BY t.id ASC)
 * lag(TUser::getAge, 1).over(w -> w.orderBy(JOrder.asc(TUser::getId))).as("prevAge")
 * }</pre>
 *
 * @param <V> the Java type produced by the window function
 */
public final class WindowExpression<V> implements SqlExpression<V> {

    /**
     * The frame unit keyword: ROWS counts physical rows; RANGE counts logical rows (peers
     * sharing the same ORDER BY value); GROUPS counts peer groups (SQL:2011, supported by
     * PostgreSQL 11+, H2, SQLite 3.28+).
     */
    public enum FrameType { ROWS, RANGE, GROUPS }

    /**
     * A single boundary of the window frame ({@code ROWS}/{@code RANGE} BETWEEN … AND …).
     *
     * <p>Factory methods:
     * <pre>{@code
     * FrameBound.unboundedPreceding()    // UNBOUNDED PRECEDING
     * FrameBound.preceding(3)            // 3 PRECEDING
     * FrameBound.currentRow()            // CURRENT ROW
     * FrameBound.following(2)            // 2 FOLLOWING
     * FrameBound.unboundedFollowing()    // UNBOUNDED FOLLOWING
     * }</pre>
     */
    public static final class FrameBound {
        /** The kind of this boundary. */
        public enum Kind {
            UNBOUNDED_PRECEDING, PRECEDING, CURRENT_ROW, FOLLOWING, UNBOUNDED_FOLLOWING
        }
        private final Kind kind;
        private final long offset;

        private FrameBound(Kind kind, long offset) {
            this.kind = kind;
            this.offset = offset;
        }

        /** {@code UNBOUNDED PRECEDING} — from the very first row of the partition. */
        public static FrameBound unboundedPreceding() { return new FrameBound(Kind.UNBOUNDED_PRECEDING, 0); }
        /** {@code n PRECEDING} — {@code n} rows before the current row. */
        public static FrameBound preceding(long n) { return new FrameBound(Kind.PRECEDING, n); }
        /** {@code CURRENT ROW}. */
        public static FrameBound currentRow() { return new FrameBound(Kind.CURRENT_ROW, 0); }
        /** {@code n FOLLOWING} — {@code n} rows after the current row. */
        public static FrameBound following(long n) { return new FrameBound(Kind.FOLLOWING, n); }
        /** {@code UNBOUNDED FOLLOWING} — up to the very last row of the partition. */
        public static FrameBound unboundedFollowing() { return new FrameBound(Kind.UNBOUNDED_FOLLOWING, 0); }

        public Kind getKind() { return kind; }
        public long getOffset() { return offset; }

        /** Returns the SQL fragment for this boundary (e.g. {@code "3 PRECEDING"}). */
        public String toSql() {
            return switch (kind) {
                case UNBOUNDED_PRECEDING -> "UNBOUNDED PRECEDING";
                case PRECEDING -> offset + " PRECEDING";
                case CURRENT_ROW -> "CURRENT ROW";
                case FOLLOWING -> offset + " FOLLOWING";
                case UNBOUNDED_FOLLOWING -> "UNBOUNDED FOLLOWING";
            };
        }
    }

    private final SqlExpression<V> function;
    private final List<SqlExpression<?>> partitionBy;
    private final List<JOrder<?>> orderBy;
    /** Frame type (ROWS/RANGE/GROUPS), or {@code null} when no explicit frame is set. */
    private final FrameType frameType;
    /** Start boundary of the frame, or {@code null} when no explicit frame is set. */
    private final FrameBound frameStart;
    /** End boundary of the frame, or {@code null} when no explicit frame is set. */
    private final FrameBound frameEnd;

    WindowExpression(SqlExpression<V> function,
                     List<SqlExpression<?>> partitionBy,
                     List<JOrder<?>> orderBy,
                     FrameType frameType,
                     FrameBound frameStart,
                     FrameBound frameEnd) {
        this.function = function;
        this.partitionBy = Collections.unmodifiableList(new ArrayList<>(partitionBy));
        this.orderBy = Collections.unmodifiableList(new ArrayList<>(orderBy));
        this.frameType = frameType;
        this.frameStart = frameStart;
        this.frameEnd = frameEnd;
    }

    /** Returns the base function or aggregate expression (the part before OVER). */
    public SqlExpression<V> getFunction() { return function; }

    /** Returns the PARTITION BY expressions (may be empty). */
    public List<SqlExpression<?>> getPartitionBy() { return partitionBy; }

    /** Returns the ORDER BY directives within the OVER clause (may be empty). */
    public List<JOrder<?>> getOrderBy() { return orderBy; }

    /** Returns the frame type (ROWS/RANGE/GROUPS), or {@code null} when no frame is specified. */
    public FrameType getFrameType() { return frameType; }

    /** Returns the frame start boundary, or {@code null} when no frame is specified. */
    public FrameBound getFrameStart() { return frameStart; }

    /** Returns the frame end boundary, or {@code null} when no frame is specified. */
    public FrameBound getFrameEnd() { return frameEnd; }

    // ------------------------------------------------------------------ //
    //  Builder
    // ------------------------------------------------------------------ //

    /**
     * Fluent builder for the OVER clause of a window function.
     *
     * @param <V> the return type of the window function
     */
    public static final class Builder<V> {

        private final SqlExpression<V> function;
        private final List<SqlExpression<?>> partitionBy = new ArrayList<>();
        private final List<JOrder<?>> orderBy = new ArrayList<>();
        private FrameType frameType = null;
        private FrameBound frameStart = null;
        private FrameBound frameEnd = null;

        Builder(SqlExpression<V> function) {
            this.function = function;
        }

        /**
         * Adds PARTITION BY columns using method references.
         *
         * <p>Multiple calls accumulate (they do not replace previous partitions).
         */
        @SafeVarargs
        public final <T> Builder<V> partitionBy(SFunction<T, ?>... props) {
            for (SFunction<T, ?> p : props) {
                partitionBy.add(ColumnExpression.of(PropertyRefResolver.resolve(p)));
            }
            return this;
        }

        /**
         * Adds PARTITION BY expressions (column refs with explicit alias, functions, etc.).
         */
        public Builder<V> partitionBy(SqlExpression<?>... exprs) {
            partitionBy.addAll(Arrays.asList(exprs));
            return this;
        }

        /**
         * Adds ORDER BY directives for the OVER clause.
         * Uses the same {@link JOrder} as the outer query's ORDER BY.
         */
        @SafeVarargs
        public final <T> Builder<V> orderBy(JOrder<T>... orders) {
            orderBy.addAll(Arrays.asList(orders));
            return this;
        }

        /**
         * Specifies a {@code ROWS BETWEEN start AND end} frame clause.
         *
         * <p>Example — running total from the beginning of the partition to the current row:
         * <pre>{@code
         * sum(TOrder::getAmount).over(w -> w
         *     .partitionBy(TOrder::getUserId)
         *     .orderBy(JOrder.asc(TOrder::getId))
         *     .rowsBetween(FrameBound.unboundedPreceding(), FrameBound.currentRow())
         * ).as("runningTotal")
         * }</pre>
         *
         * @param start the start boundary
         * @param end   the end boundary
         */
        public Builder<V> rowsBetween(FrameBound start, FrameBound end) {
            this.frameType = FrameType.ROWS;
            this.frameStart = start;
            this.frameEnd = end;
            return this;
        }

        /**
         * Specifies a {@code RANGE BETWEEN start AND end} frame clause.
         *
         * <p>RANGE uses logical boundaries based on the ORDER BY value rather than physical row counts.
         *
         * @param start the start boundary
         * @param end   the end boundary
         */
        public Builder<V> rangeBetween(FrameBound start, FrameBound end) {
            this.frameType = FrameType.RANGE;
            this.frameStart = start;
            this.frameEnd = end;
            return this;
        }

        /**
         * Specifies a {@code GROUPS BETWEEN start AND end} frame clause (SQL:2011).
         *
         * <p>Supported by PostgreSQL 11+, H2 2.x, SQLite 3.28+.
         *
         * @param start the start boundary
         * @param end   the end boundary
         */
        public Builder<V> groupsBetween(FrameBound start, FrameBound end) {
            this.frameType = FrameType.GROUPS;
            this.frameStart = start;
            this.frameEnd = end;
            return this;
        }

        /** Builds the {@link WindowExpression}. */
        public WindowExpression<V> build() {
            return new WindowExpression<>(function, partitionBy, orderBy, frameType, frameStart, frameEnd);
        }
    }
}
