package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.dialect.Oracle11gDialect;
import io.github.jsbxyyx.jdbcdsl.dialect.OracleDialect;
import io.github.jsbxyyx.jdbcdsl.dialect.SqlServerDialect;
import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static io.github.jsbxyyx.jdbcdsl.Scalar.scalar;
import static io.github.jsbxyyx.jdbcdsl.SqlFunctions.col;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class LockSafetyTest {
    private final SelectSpec<TUser, TUser> locked =
            SelectBuilder.from(TUser.class).forUpdate().mapToEntity();
    private final SelectSpec<TUser, TUser> unlocked =
            SelectBuilder.from(TUser.class).mapToEntity();
    private final SelectSpec<TUser, Long> lockedIds =
            SelectBuilder.from(TUser.class).select(TUser::getId).forUpdate().mapTo(Long.class);

    @Test
    void setOperationRejectsLocksInEveryBranch() {
        assertLockRejected(() ->
                SqlRenderer.renderUnion(UnionSpec.of(locked).unionAll(unlocked).build()));
        assertLockRejected(() ->
                SqlRenderer.renderUnion(UnionSpec.of(unlocked).unionAll(locked).build()));
    }

    @Test
    void cteAndDerivedTableRejectNestedLocksForSelectAndCount() {
        var cte = SelectBuilder.fromCte("users", TUser.class)
                .withCte("users", locked)
                .mapToEntity();
        var derived = SelectBuilder.fromSubquery(locked, "sub", TUser.class).mapToEntity();
        assertLockRejected(() -> SqlRenderer.renderSelect(cte));
        assertLockRejected(() -> SqlRenderer.renderCount(cte));
        assertLockRejected(() -> SqlRenderer.renderSelect(derived));
        assertLockRejected(() -> SqlRenderer.renderCount(derived));
    }

    @Test
    void joinedAndPredicateSubqueriesRejectNestedLocks() {
        var joined = SelectBuilder.from(TUser.class)
                .leftJoinSubquery(locked, TUser.class, "j", on -> on.raw("j.id = t.id"))
                .mapToEntity();
        var predicate = SelectBuilder.from(TUser.class)
                .where(w -> w.inSubquery(col(TUser::getId), lockedIds))
                .mapToEntity();
        var scalarQuery = SelectBuilder.from(TUser.class)
                .where(w -> w.eqScalar(col(TUser::getId), scalar(lockedIds)))
                .mapToEntity();
        assertLockRejected(() -> SqlRenderer.renderSelect(joined));
        assertLockRejected(() -> SqlRenderer.renderCount(joined));
        assertLockRejected(() -> SqlRenderer.renderSelect(predicate));
        assertLockRejected(() -> SqlRenderer.renderSelect(scalarQuery));
    }

    @Test
    void dmlPredicateSubqueryRejectsNestedLock() {
        var update = UpdateBuilder.from(TUser.class)
                .set(TUser::getStatus, "ACTIVE")
                .where(w -> w.inSubquery(col(TUser::getId), lockedIds))
                .build();
        assertLockRejected(() -> SqlRenderer.renderUpdate(update));
    }

    @Test
    void standaloneCountRejectsLockButPageCountCanOmitItsRootLock() {
        var distinct = SelectBuilder.from(TUser.class).distinct().forUpdate().mapToEntity();
        assertThatThrownBy(() -> SqlRenderer.renderCount(locked))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("COUNT");
        assertThatThrownBy(() -> SqlRenderer.renderCount(distinct))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("COUNT");
        assertThat(SqlRenderer.renderCountForPage(distinct, EntityMetaReader::read)
                        .getSql())
                .startsWith("SELECT COUNT(*) FROM (SELECT DISTINCT")
                .doesNotContain("FOR UPDATE");
    }

    @Test
    void sqlServerLockIsRejectedBeforeExecutingAnyQuery() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        JdbcDslExecutor executor = new JdbcDslExecutor(jdbc, new SqlServerDialect());
        assertThatThrownBy(() -> executor.select(locked))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("SQL Server");
        assertThatThrownBy(() -> executor.selectPage(locked, JPageable.of(0, 10)))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("SQL Server");
        verifyNoInteractions(jdbc);
    }

    @Test
    void oracleLockedPageIsRejectedBeforeExecutingCount() {
        for (var dialect : java.util.List.of(new OracleDialect(), new Oracle11gDialect())) {
            NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
            JdbcDslExecutor executor = new JdbcDslExecutor(jdbc, dialect);
            assertThatThrownBy(() -> executor.selectPage(locked, JPageable.of(0, 10)))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("row locks");
            verifyNoInteractions(jdbc);
        }
    }

    private static void assertLockRejected(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Row locks");
    }
}
