package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JdbcDslRepositoryTest {

    @Test
    void builderFactoriesPreserveBoundEntityType() {
        JdbcDslExecutor executor = mock(JdbcDslExecutor.class);
        TestUserRepository repository = new TestUserRepository(executor);

        assertThat(repository.getEntityClass()).isEqualTo(TUser.class);
        assertThat(repository.selectBuilder()
                .where(w -> w.eq(TUser::getStatus, "ACTIVE"))
                .mapToEntity()
                .getEntityClass()).isEqualTo(TUser.class);
        assertThat(repository.insertBuilder().build().getEntityClass()).isEqualTo(TUser.class);
        assertThat(repository.updateBuilder()
                .where(w -> w.eq(TUser::getId, 1L))
                .build()
                .getEntityClass()).isEqualTo(TUser.class);
        assertThat(repository.deleteBuilder()
                .where(w -> w.eq(TUser::getId, 1L))
                .build()
                .getEntityClass()).isEqualTo(TUser.class);
        assertThat(repository.upsertBuilder()
                .onConflict(TUser::getId)
                .build()
                .getEntityClass()).isEqualTo(TUser.class);
    }

    @Test
    void commonOperationsDelegateToSharedExecutor() {
        JdbcDslExecutor executor = mock(JdbcDslExecutor.class);
        TestUserRepository repository = new TestUserRepository(executor);
        TUser user = new TUser();

        UpsertSpec<TUser> upsertSpec = repository.upsertBuilder()
                .onConflict(TUser::getId)
                .build();

        repository.save(user);
        repository.upsert(upsertSpec, user);
        repository.updateById(user);
        repository.deleteById(7L);

        verify(executor).save(user);
        verify(executor).upsert(upsertSpec, user);
        verify(executor).updateById(user);
        verify(executor).deleteById(TUser.class, 7L);
    }

    private static final class TestUserRepository extends JdbcDslRepository<TUser, Long> {
        private TestUserRepository(JdbcDslExecutor executor) {
            super(TUser.class, executor);
        }
    }
}
