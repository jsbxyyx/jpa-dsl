package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JdbcDslRepositoryTest {

    @Test
    void buildersCanBeCreatedDirectlyWithoutRepositoryFactories() {
        JdbcDslExecutor executor = mock(JdbcDslExecutor.class);
        TestUserRepository repository = new TestUserRepository(executor);

        assertThat(repository.getEntityClass()).isEqualTo(TUser.class);
        assertThat(SelectBuilder.from(TUser.class).mapToEntity().getEntityClass())
                .isEqualTo(TUser.class);
        assertThat(InsertBuilder.into(TUser.class).build().getEntityClass()).isEqualTo(TUser.class);
        assertThat(UpdateBuilder.from(TUser.class)
                        .set(TUser::getStatus, "ACTIVE")
                        .where(w -> w.eq(TUser::getId, 1L))
                        .build()
                        .getEntityClass())
                .isEqualTo(TUser.class);
        assertThat(DeleteBuilder.from(TUser.class)
                        .where(w -> w.eq(TUser::getId, 1L))
                        .build()
                        .getEntityClass())
                .isEqualTo(TUser.class);
        assertThat(UpsertBuilder.into(TUser.class)
                        .onConflict(TUser::getId)
                        .build()
                        .getEntityClass())
                .isEqualTo(TUser.class);
    }

    @Test
    void commonOperationsDelegateToSharedExecutor() {
        JdbcDslExecutor executor = mock(JdbcDslExecutor.class);
        TestUserRepository repository = new TestUserRepository(executor);
        TUser user = new TUser();

        UpsertSpec<TUser> upsertSpec =
                UpsertBuilder.into(TUser.class).onConflict(TUser::getId).build();

        repository.save(user);
        repository.upsert(upsertSpec, user);
        repository.updateById(user);
        repository.deleteById(7L);

        verify(executor).save(user);
        verify(executor).upsert(upsertSpec, user);
        verify(executor).updateById(user);
        verify(executor).deleteById(TUser.class, 7L);
    }

    @Test
    void saveAllAcceptsSetAndDelegatesToBatchInsert() {
        JdbcDslExecutor executor = mock(JdbcDslExecutor.class);
        TestUserRepository repository = new TestUserRepository(executor);
        TUser first = new TUser();
        TUser second = new TUser();
        LinkedHashSet<TUser> users = new LinkedHashSet<>(List.of(first, second));

        repository.saveAll(users);

        org.mockito.ArgumentCaptor<BatchInsertSpec<TUser>> captor =
                org.mockito.ArgumentCaptor.forClass(BatchInsertSpec.class);
        verify(executor).executeBatchInsert(captor.capture());
        assertThat(captor.getValue().getEntityClass()).isEqualTo(TUser.class);
        assertThat(captor.getValue().getRows()).containsExactly(first, second);
        assertThat(captor.getValue().getColumnNames()).isEmpty();
    }

    @Test
    void chunkedSaveAllDelegatesAndRejectsInvalidSize() {
        JdbcDslExecutor executor = mock(JdbcDslExecutor.class);
        TestUserRepository repository = new TestUserRepository(executor);
        TUser first = new TUser();
        TUser second = new TUser();
        repository.saveAll(List.of(first, second), 1);
        org.mockito.ArgumentCaptor<BatchInsertSpec<TUser>> captor =
                org.mockito.ArgumentCaptor.forClass(BatchInsertSpec.class);
        verify(executor).executeBatchInsert(captor.capture(), org.mockito.ArgumentMatchers.eq(1));
        assertThat(captor.getValue().getRows()).containsExactly(first, second);
        assertThatThrownBy(() -> repository.saveAll(List.of(first), 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void countWithSpecDelegatesToSharedExecutor() {
        JdbcDslExecutor executor = mock(JdbcDslExecutor.class);
        TestUserRepository repository = new TestUserRepository(executor);
        SelectSpec<TUser, TUser> spec = SelectBuilder.from(TUser.class)
                .where(w -> w.eq(TUser::getStatus, "ACTIVE"))
                .mapToEntity();

        repository.count(spec);

        verify(executor).count(spec);
    }

    @Test
    void selectByIdDelegatesToSharedExecutor() {
        JdbcDslExecutor executor = mock(JdbcDslExecutor.class);
        TestUserRepository repository = new TestUserRepository(executor);

        repository.selectById(7L);

        verify(executor).selectById(TUser.class, 7L);
    }

    @Test
    void listWithUnpagedSortDelegatesToSharedExecutor() {
        JdbcDslExecutor executor = mock(JdbcDslExecutor.class);
        TestUserRepository repository = new TestUserRepository(executor);
        SelectSpec<TUser, TUser> spec = SelectBuilder.from(TUser.class).mapToEntity();
        JPageable<TUser> pageable = JPageable.unpaged(JSort.byDesc(TUser::getId));

        repository.list(spec, pageable);

        verify(executor).select(spec, pageable);
    }

    @Test
    void forEachDelegatesToSharedExecutor() {
        JdbcDslExecutor executor = mock(JdbcDslExecutor.class);
        TestUserRepository repository = new TestUserRepository(executor);
        SelectSpec<TUser, TUser> spec = SelectBuilder.from(TUser.class).mapToEntity();
        java.util.function.Consumer<TUser> consumer = user -> {};
        repository.forEach(spec, consumer);
        verify(executor).selectEach(spec, consumer);
        JPageable<TUser> page = JPageable.of(1, 10);
        repository.forEach(spec, page, consumer);
        verify(executor).selectEach(spec, page, consumer);
    }

    @Test
    void sliceDelegatesToSharedExecutor() {
        JdbcDslExecutor executor = mock(JdbcDslExecutor.class);
        TestUserRepository repository = new TestUserRepository(executor);
        SelectSpec<TUser, TUser> spec = SelectBuilder.from(TUser.class).mapToEntity();
        JPageable<TUser> pageable = JPageable.of(1, 10);
        repository.slice(spec, pageable);
        verify(executor).selectSlice(spec, pageable);
    }

    private static final class TestUserRepository extends JdbcDslRepository<TUser, Long> {
        private TestUserRepository(JdbcDslExecutor executor) {
            super(TUser.class, executor);
        }
    }
}
