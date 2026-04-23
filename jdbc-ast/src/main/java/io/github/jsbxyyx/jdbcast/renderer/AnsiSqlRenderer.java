package io.github.jsbxyyx.jdbcast.renderer;

import io.github.jsbxyyx.jdbcast.clause.ColumnAssignment;
import io.github.jsbxyyx.jdbcast.clause.CteDef;
import io.github.jsbxyyx.jdbcast.clause.JoinClause;
import io.github.jsbxyyx.jdbcast.clause.OrderItem;
import io.github.jsbxyyx.jdbcast.condition.AndCondition;
import io.github.jsbxyyx.jdbcast.condition.BetweenCondition;
import io.github.jsbxyyx.jdbcast.condition.CompareCondition;
import io.github.jsbxyyx.jdbcast.condition.Condition;
import io.github.jsbxyyx.jdbcast.condition.ExistsCondition;
import io.github.jsbxyyx.jdbcast.condition.InCondition;
import io.github.jsbxyyx.jdbcast.condition.LikeCondition;
import io.github.jsbxyyx.jdbcast.condition.NotCondition;
import io.github.jsbxyyx.jdbcast.condition.NullCondition;
import io.github.jsbxyyx.jdbcast.condition.OrCondition;
import io.github.jsbxyyx.jdbcast.condition.RawCondition;
import io.github.jsbxyyx.jdbcast.expr.AggExpr;
import io.github.jsbxyyx.jdbcast.expr.AliasedExpr;
import io.github.jsbxyyx.jdbcast.expr.CaseExpr;
import io.github.jsbxyyx.jdbcast.expr.CastExpr;
import io.github.jsbxyyx.jdbcast.expr.ColExpr;
import io.github.jsbxyyx.jdbcast.expr.Expr;
import io.github.jsbxyyx.jdbcast.expr.FunctionExpr;
import io.github.jsbxyyx.jdbcast.expr.LiteralExpr;
import io.github.jsbxyyx.jdbcast.expr.RawExpr;
import io.github.jsbxyyx.jdbcast.expr.StarExpr;
import io.github.jsbxyyx.jdbcast.expr.SubqueryExpr;
import io.github.jsbxyyx.jdbcast.expr.WindowExpr;
import io.github.jsbxyyx.jdbcast.stmt.DeleteStatement;
import io.github.jsbxyyx.jdbcast.stmt.InsertStatement;
import io.github.jsbxyyx.jdbcast.stmt.SelectStatement;
import io.github.jsbxyyx.jdbcast.stmt.UpdateStatement;

import io.github.jsbxyyx.jdbcast.renderer.dialect.LimitOffsetDialect;

import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * ANSI SQL renderer — converts the SQL AST to a named-parameter SQL string.
 *
 * <p>Pagination syntax is delegated to a pluggable {@link PaginationDialect} SPI.
 * Pass the appropriate dialect for your database:
 *
 * <pre>{@code
 * // MySQL / PostgreSQL / H2 / SQLite (default)
 * new AnsiSqlRenderer(meta, LimitOffsetDialect.INSTANCE);
 *
 * // SQL Server / Oracle 12c+ / DB2
 * new AnsiSqlRenderer(meta, OffsetFetchDialect.INSTANCE);
 *
 * // Custom dialect (lambda)
 * new AnsiSqlRenderer(meta, (sb, limit, offset, ctx) -> { ... });
 * }</pre>
 *
 * <p>Other database-specific syntax (locking clauses, UPSERT, etc.) can be handled
 * by overriding the relevant {@code render*} methods in a subclass.
 */
public class AnsiSqlRenderer implements SqlRenderer {

    protected final MetaResolver     meta;
    protected final PaginationDialect paginationDialect;

    /**
     * Creates a renderer with the default {@link LimitOffsetDialect}
     * ({@code LIMIT :n OFFSET :m} — MySQL, PostgreSQL, H2, SQLite).
     */
    public AnsiSqlRenderer(MetaResolver meta) {
        this(meta, LimitOffsetDialect.INSTANCE);
    }

    /**
     * Creates a renderer with an explicit {@link PaginationDialect}.
     *
     * @param meta             table / column name resolver
     * @param paginationDialect dialect that renders the LIMIT / OFFSET clause
     */
    public AnsiSqlRenderer(MetaResolver meta, PaginationDialect paginationDialect) {
        this.meta              = Objects.requireNonNull(meta,              "meta");
        this.paginationDialect = Objects.requireNonNull(paginationDialect, "paginationDialect");
    }

    /** Returns the {@link MetaResolver} used by this renderer. */
    public MetaResolver getMetaResolver() { return meta; }

    /** Returns the {@link PaginationDialect} used by this renderer. */
    public PaginationDialect getPaginationDialect() { return paginationDialect; }

    // ================================================================== //
    //  Public render entry points
    // ================================================================== //

    @Override
    public RenderedSql render(SelectStatement stmt) {
        RenderContext ctx = new RenderContext();
        String sql = renderSelect(stmt, ctx);
        return new RenderedSql(sql, ctx.params());
    }

    @Override
    public RenderedSql render(InsertStatement stmt) {
        RenderContext ctx = new RenderContext();
        String sql = renderInsert(stmt, ctx);
        return new RenderedSql(sql, ctx.params());
    }

    @Override
    public RenderedSql render(UpdateStatement stmt) {
        RenderContext ctx = new RenderContext();
        String sql = renderUpdate(stmt, ctx);
        return new RenderedSql(sql, ctx.params());
    }

    @Override
    public RenderedSql render(DeleteStatement stmt) {
        RenderContext ctx = new RenderContext();
        String sql = renderDelete(stmt, ctx);
        return new RenderedSql(sql, ctx.params());
    }

    // ================================================================== //
    //  SELECT rendering
    // ================================================================== //

    protected String renderSelect(SelectStatement stmt, RenderContext ctx) {
        StringBuilder sb = new StringBuilder();

        // WITH
        if (!stmt.with().isEmpty()) {
            sb.append("WITH ");
            StringJoiner cj = new StringJoiner(", ");
            for (CteDef cte : stmt.with()) {
                cj.add(cte.name() + " AS (" + renderSelect(cte.query(), ctx) + ")");
            }
            sb.append(cj).append(" ");
        }

        // SELECT [DISTINCT]
        sb.append("SELECT ");
        if (stmt.distinct()) sb.append("DISTINCT ");

        if (stmt.select().isEmpty()) {
            sb.append("*");
        } else {
            sb.append(joinExprs(stmt.select(), ctx));
        }

        // FROM
        sb.append(" FROM ").append(renderTableRef(stmt.from().entityClass(), stmt.from().alias()));

        // JOINs
        for (JoinClause join : stmt.joins()) {
            sb.append(" ").append(join.type().keyword()).append(" JOIN ");
            sb.append(renderTableRef(join.table().entityClass(), join.table().alias()));
            if (join.on() != null) {
                sb.append(" ON ").append(renderCondition(join.on(), ctx));
            }
        }

        // WHERE
        if (stmt.where() != null) {
            sb.append(" WHERE ").append(renderCondition(stmt.where(), ctx));
        }

        // GROUP BY
        if (!stmt.groupBy().isEmpty()) {
            sb.append(" GROUP BY ").append(joinExprs(stmt.groupBy(), ctx));
        }

        // HAVING
        if (stmt.having() != null) {
            sb.append(" HAVING ").append(renderCondition(stmt.having(), ctx));
        }

        // ORDER BY
        if (!stmt.orderBy().isEmpty()) {
            sb.append(" ORDER BY ").append(
                    stmt.orderBy().stream()
                            .map(o -> renderOrderItem(o, ctx))
                            .collect(Collectors.joining(", ")));
        }

        // LIMIT / OFFSET
        renderLimitOffset(sb, stmt.limit(), stmt.offset(), ctx);

        // FOR UPDATE / FOR SHARE
        if (stmt.lockMode() != null) {
            sb.append(switch (stmt.lockMode()) {
                case UPDATE            -> " FOR UPDATE";
                case UPDATE_NOWAIT     -> " FOR UPDATE NOWAIT";
                case UPDATE_SKIP_LOCKED -> " FOR UPDATE SKIP LOCKED";
                case SHARE             -> " FOR SHARE";
            });
        }

        // SET OP (UNION / INTERSECT / EXCEPT)
        if (stmt.setOp() != null) {
            sb.append(" ").append(stmt.setOp().type().keyword())
              .append(" ").append(renderSelect(stmt.setOp().right(), ctx));
        }

        return sb.toString();
    }

    /**
     * Delegates to {@link PaginationDialect#renderPage} when limit or offset is present.
     * Override in a subclass only when the entire pagination strategy needs replacing
     * (e.g. legacy Oracle ROWNUM wrapping); for standard databases, prefer supplying
     * a {@link PaginationDialect} to the constructor.
     */
    protected void renderLimitOffset(StringBuilder sb, Long limit, Long offset, RenderContext ctx) {
        if (limit != null || offset != null) {
            paginationDialect.renderPage(sb, limit, offset, ctx);
        }
    }

    // ================================================================== //
    //  INSERT rendering
    // ================================================================== //

    protected String renderInsert(InsertStatement stmt, RenderContext ctx) {
        String table = meta.tableName(stmt.entity());
        List<ColumnAssignment> cols = stmt.assignments();
        if (cols.isEmpty()) {
            throw new IllegalStateException(
                    "InsertStatement has no column assignments. " +
                    "Either call .set(...) on the builder or use an entity-based executor.");
        }
        StringJoiner colJoiner = new StringJoiner(", ");
        StringJoiner valJoiner = new StringJoiner(", ");
        for (ColumnAssignment a : cols) {
            colJoiner.add(meta.columnName(a.getter()));
            valJoiner.add(renderExpr(a.value(), ctx));
        }
        String sql = "INSERT INTO " + table + " (" + colJoiner + ") VALUES (" + valJoiner + ")";
        if (!stmt.returningCols().isEmpty()) {
            sql += " RETURNING " + String.join(", ", stmt.returningCols());
        }
        return sql;
    }

    // ================================================================== //
    //  UPDATE rendering
    // ================================================================== //

    protected String renderUpdate(UpdateStatement stmt, RenderContext ctx) {
        String table = meta.tableName(stmt.entity());
        String alias = stmt.tableAlias();
        StringBuilder sb = new StringBuilder("UPDATE ");
        sb.append(alias != null ? table + " " + alias : table);
        sb.append(" SET ");
        sb.append(stmt.assignments().stream()
                .map(a -> meta.columnName(a.getter()) + " = " + renderExpr(a.value(), ctx))
                .collect(Collectors.joining(", ")));
        if (stmt.where() != null) {
            sb.append(" WHERE ").append(renderCondition(stmt.where(), ctx));
        }
        if (!stmt.returningCols().isEmpty()) {
            sb.append(" RETURNING ").append(String.join(", ", stmt.returningCols()));
        }
        return sb.toString();
    }

    // ================================================================== //
    //  DELETE rendering
    // ================================================================== //

    protected String renderDelete(DeleteStatement stmt, RenderContext ctx) {
        String table = meta.tableName(stmt.entity());
        String alias = stmt.tableAlias();
        StringBuilder sb = new StringBuilder("DELETE FROM ");
        sb.append(alias != null ? table + " " + alias : table);
        if (stmt.where() != null) {
            sb.append(" WHERE ").append(renderCondition(stmt.where(), ctx));
        }
        if (!stmt.returningCols().isEmpty()) {
            sb.append(" RETURNING ").append(String.join(", ", stmt.returningCols()));
        }
        return sb.toString();
    }

    // ================================================================== //
    //  Expression rendering
    // ================================================================== //

    protected String renderExpr(Expr<?> expr, RenderContext ctx) {
        if (expr instanceof ColExpr<?> c) {
            return renderCol(c);
        }
        if (expr instanceof LiteralExpr<?> lit) {
            return ctx.addParam(lit.value());
        }
        if (expr instanceof AliasedExpr<?> a) {
            return renderExpr(a.inner(), ctx) + " AS " + a.alias();
        }
        if (expr instanceof StarExpr s) {
            return s.tableAlias() != null ? s.tableAlias() + ".*" : "*";
        }
        if (expr instanceof RawExpr<?> r) {
            ctx.mergeParams(r.params());
            return r.sql();
        }
        if (expr instanceof FunctionExpr<?> fn) {
            return fn.name() + "(" + joinExprs(fn.args(), ctx) + ")";
        }
        if (expr instanceof AggExpr<?> agg) {
            // Empty args → FUNC() (e.g. ROW_NUMBER()); pass StarExpr.ALL explicitly for COUNT(*)
            String args = agg.args().isEmpty() ? "" : joinExprs(agg.args(), ctx);
            return agg.name() + "(" + (agg.distinct() ? "DISTINCT " : "") + args + ")";
        }
        if (expr instanceof CastExpr<?> c) {
            return "CAST(" + renderExpr(c.inner(), ctx) + " AS " + c.sqlType() + ")";
        }
        if (expr instanceof SubqueryExpr<?> s) {
            return "(" + renderSelect(s.subquery(), ctx) + ")";
        }
        if (expr instanceof WindowExpr<?> w) {
            return renderWindowExpr(w, ctx);
        }
        if (expr instanceof CaseExpr<?> c) {
            return renderCaseExpr(c, ctx);
        }
        throw new IllegalArgumentException("Unknown Expr type: " + expr.getClass().getName());
    }

    protected String renderCol(ColExpr<?> c) {
        String colName = meta.columnName(c.getter());
        return c.tableAlias() != null ? c.tableAlias() + "." + colName : colName;
    }

    protected String renderWindowExpr(WindowExpr<?> w, RenderContext ctx) {
        StringBuilder sb = new StringBuilder(renderExpr(w.function(), ctx));
        sb.append(" OVER (");
        boolean hasPrev = false;

        if (!w.partitionBy().isEmpty()) {
            sb.append("PARTITION BY ").append(joinExprs(w.partitionBy(), ctx));
            hasPrev = true;
        }
        if (!w.orderBy().isEmpty()) {
            if (hasPrev) sb.append(" ");
            sb.append("ORDER BY ").append(
                    w.orderBy().stream()
                            .map(o -> renderOrderItem(o, ctx))
                            .collect(Collectors.joining(", ")));
            hasPrev = true;
        }
        if (w.frameType() != null) {
            if (hasPrev) sb.append(" ");
            sb.append(w.frameType().name())
              .append(" BETWEEN ")
              .append(w.frameStart().toSql())
              .append(" AND ")
              .append(w.frameEnd().toSql());
        }
        sb.append(")");
        return sb.toString();
    }

    protected String renderCaseExpr(CaseExpr<?> c, RenderContext ctx) {
        StringBuilder sb = new StringBuilder("CASE");
        if (c.operand() != null) {
            sb.append(" ").append(renderExpr(c.operand(), ctx));
        }
        for (CaseExpr.WhenThen<?> wt : c.whens()) {
            sb.append(" WHEN ");
            if (wt.when() instanceof Condition cond) {
                sb.append(renderCondition(cond, ctx));
            } else if (wt.when() instanceof Expr<?> e) {
                sb.append(renderExpr(e, ctx));
            } else {
                sb.append(ctx.addParam(wt.when()));
            }
            sb.append(" THEN ").append(renderExpr(wt.then(), ctx));
        }
        if (c.elseExpr() != null) {
            sb.append(" ELSE ").append(renderExpr(c.elseExpr(), ctx));
        }
        sb.append(" END");
        return sb.toString();
    }

    protected String renderOrderItem(OrderItem o, RenderContext ctx) {
        String expr = renderExpr(o.expr(), ctx);
        String dir  = o.asc() ? " ASC" : " DESC";
        String nulls = "";
        if (o.nullsFirst() != null) {
            nulls = o.nullsFirst() ? " NULLS FIRST" : " NULLS LAST";
        }
        return expr + dir + nulls;
    }

    // ================================================================== //
    //  Condition rendering
    // ================================================================== //

    protected String renderCondition(Condition condition, RenderContext ctx) {
        if (condition instanceof CompareCondition cc) {
            String right = (cc.right() instanceof Expr<?> e)
                    ? renderExpr(e, ctx)
                    : ctx.addParam(cc.right());
            return renderExpr(cc.left(), ctx) + " " + cc.op().symbol() + " " + right;
        }
        if (condition instanceof AndCondition and) {
            return and.children().stream()
                    .map(c -> "(" + renderCondition(c, ctx) + ")")
                    .collect(Collectors.joining(" AND "));
        }
        if (condition instanceof OrCondition or) {
            return or.children().stream()
                    .map(c -> "(" + renderCondition(c, ctx) + ")")
                    .collect(Collectors.joining(" OR "));
        }
        if (condition instanceof NotCondition not) {
            return "NOT (" + renderCondition(not.inner(), ctx) + ")";
        }
        if (condition instanceof NullCondition nc) {
            return renderExpr(nc.column(), ctx) + (nc.isNull() ? " IS NULL" : " IS NOT NULL");
        }
        if (condition instanceof InCondition in) {
            String vals = in.values().stream()
                    .map(v -> (v instanceof Expr<?> e) ? renderExpr(e, ctx) : ctx.addParam(v))
                    .collect(Collectors.joining(", "));
            return renderExpr(in.column(), ctx) + (in.negated() ? " NOT IN" : " IN") + " (" + vals + ")";
        }
        if (condition instanceof BetweenCondition bc) {
            String low  = (bc.low()  instanceof Expr<?> e) ? renderExpr(e, ctx) : ctx.addParam(bc.low());
            String high = (bc.high() instanceof Expr<?> e) ? renderExpr(e, ctx) : ctx.addParam(bc.high());
            return renderExpr(bc.column(), ctx)
                    + (bc.negated() ? " NOT BETWEEN " : " BETWEEN ") + low + " AND " + high;
        }
        if (condition instanceof LikeCondition lc) {
            return renderExpr(lc.column(), ctx)
                    + (lc.negated() ? " NOT LIKE " : " LIKE ")
                    + ctx.addParam(lc.pattern());
        }
        if (condition instanceof ExistsCondition ec) {
            return (ec.negated() ? "NOT EXISTS " : "EXISTS ")
                    + "(" + renderSelect(ec.subquery(), ctx) + ")";
        }
        if (condition instanceof RawCondition rc) {
            ctx.mergeParams(rc.params());
            return rc.sql();
        }
        throw new IllegalArgumentException("Unknown Condition type: " + condition.getClass().getName());
    }

    // ================================================================== //
    //  Helpers
    // ================================================================== //

    protected String renderTableRef(Class<?> entity, String alias) {
        String name = meta.tableName(entity);
        return alias != null ? name + " " + alias : name;
    }

    protected String joinExprs(List<? extends Expr<?>> exprs, RenderContext ctx) {
        return exprs.stream()
                .map(e -> renderExpr(e, ctx))
                .collect(Collectors.joining(", "));
    }
}
