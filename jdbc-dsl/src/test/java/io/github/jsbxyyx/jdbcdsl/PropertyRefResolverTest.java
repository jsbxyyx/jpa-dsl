package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.cache.JdbcDslCacheManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PropertyRefResolver} including SerializedLambda caching optimization.
 */
class PropertyRefResolverTest {

    static class TestEntity {
        private Long id;
        private String name;
        private Integer age;
        private Boolean active;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getAge() {
            return age;
        }

        public void setAge(Integer age) {
            this.age = age;
        }

        public Boolean isActive() {
            return active;
        }

        public void setActive(Boolean active) {
            this.active = active;
        }
    }

    @BeforeEach
    void setUp() {
        // Reset cache manager for each test
        PropertyRefResolver.setCacheManager(new JdbcDslCacheManager());
    }

    @Test
    void testResolveGetterMethodReference() {
        PropertyRef ref = PropertyRefResolver.resolve(TestEntity::getName);

        assertEquals(TestEntity.class, ref.ownerClass());
        assertEquals("name", ref.propertyName());
    }

    @Test
    void testResolveBooleanGetterMethodReference() {
        PropertyRef ref = PropertyRefResolver.resolve(TestEntity::isActive);

        assertEquals(TestEntity.class, ref.ownerClass());
        assertEquals("active", ref.propertyName());
    }

    @Test
    void testResolveMultipleReferences() {
        PropertyRef ref1 = PropertyRefResolver.resolve(TestEntity::getId);
        PropertyRef ref2 = PropertyRefResolver.resolve(TestEntity::getName);
        PropertyRef ref3 = PropertyRefResolver.resolve(TestEntity::getAge);

        assertEquals("id", ref1.propertyName());
        assertEquals("name", ref2.propertyName());
        assertEquals("age", ref3.propertyName());
    }

    @Test
    void testCachingBehavior() {
        // First call - should populate both caches
        PropertyRef ref1 = PropertyRefResolver.resolve(TestEntity::getName);

        // Second call with same method reference - should hit both caches
        PropertyRef ref2 = PropertyRefResolver.resolve(TestEntity::getName);

        // Verify same result
        assertEquals(ref1.ownerClass(), ref2.ownerClass());
        assertEquals(ref1.propertyName(), ref2.propertyName());

        // Verify cache is working by checking cache stats
        JdbcDslCacheManager cacheManager = new JdbcDslCacheManager();
        PropertyRefResolver.setCacheManager(cacheManager);

        // Warm up caches
        PropertyRefResolver.resolve(TestEntity::getName);
        PropertyRefResolver.resolve(TestEntity::getName);
        PropertyRefResolver.resolve(TestEntity::getName);

        // Both caches should have entries
        assertTrue(
                cacheManager.getSerializedLambdaCache().estimatedSize() > 0,
                "SerializedLambda cache should have entries");
        assertTrue(cacheManager.getPropertyRefCache().estimatedSize() > 0, "PropertyRef cache should have entries");
    }

    @Test
    void testLambdaClassCacheReducesReflection() {
        JdbcDslCacheManager cacheManager = new JdbcDslCacheManager();
        PropertyRefResolver.setCacheManager(cacheManager);

        // Create lambda instances explicitly to ensure same Class object
        SFunction<TestEntity, String> nameRef = TestEntity::getName;
        SFunction<TestEntity, Integer> ageRef = TestEntity::getAge;

        // First call - populates SerializedLambda cache
        PropertyRefResolver.resolve(nameRef);
        long lambdaCacheSize1 = cacheManager.getSerializedLambdaCache().estimatedSize();

        // Second call with SAME lambda instance - should reuse SerializedLambda from cache
        PropertyRefResolver.resolve(nameRef);
        long lambdaCacheSize2 = cacheManager.getSerializedLambdaCache().estimatedSize();

        // Cache size should remain the same (no new entries)
        assertEquals(
                lambdaCacheSize1, lambdaCacheSize2, "SerializedLambda cache should not grow for same lambda instance");

        // Different method reference should add new entry
        PropertyRefResolver.resolve(ageRef);
        long lambdaCacheSize3 = cacheManager.getSerializedLambdaCache().estimatedSize();

        assertTrue(
                lambdaCacheSize3 > lambdaCacheSize2,
                "Different method reference should add new SerializedLambda cache entry");
    }

    @Test
    void testInvalidLambdaBodyThrowsException() {
        // Lambda body (not a method reference) should throw exception
        assertThrows(IllegalArgumentException.class, () -> {
            PropertyRefResolver.resolve((TestEntity e) -> e.getName());
        });
    }
}
