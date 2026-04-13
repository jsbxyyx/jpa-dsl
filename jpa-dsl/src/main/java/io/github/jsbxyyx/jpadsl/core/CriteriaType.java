package io.github.jsbxyyx.jpadsl.core;

/**
 * Enumerates the supported query condition types.
 *
 * <p>Used together with {@link Criteria} to represent query conditions as plain objects.
 * Not used internally by {@link SpecificationBuilder} or {@link SpecificationDsl};
 * provided as a convenience for consumers who collect criteria before translating them
 * into {@link org.springframework.data.jpa.domain.Specification} instances.
 */
public enum CriteriaType {
    EQUAL,
    NOT_EQUAL,
    LIKE,
    IN,
    BETWEEN,
    GREATER_THAN,
    LESS_THAN,
    GREATER_THAN_OR_EQUAL,
    LESS_THAN_OR_EQUAL,
    IS_NULL,
    IS_NOT_NULL
}
