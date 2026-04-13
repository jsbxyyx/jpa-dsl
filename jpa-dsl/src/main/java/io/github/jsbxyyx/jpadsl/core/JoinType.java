package io.github.jsbxyyx.jpadsl.core;

/**
 * Wrapper enum for JPA join types to avoid direct dependency on Jakarta Persistence in client code.
 */
public enum JoinType {
    INNER(jakarta.persistence.criteria.JoinType.INNER),
    LEFT(jakarta.persistence.criteria.JoinType.LEFT),
    RIGHT(jakarta.persistence.criteria.JoinType.RIGHT);

    private final jakarta.persistence.criteria.JoinType jpaJoinType;

    JoinType(jakarta.persistence.criteria.JoinType jpaJoinType) {
        this.jpaJoinType = jpaJoinType;
    }

    public jakarta.persistence.criteria.JoinType getJpaJoinType() {
        return jpaJoinType;
    }
}
