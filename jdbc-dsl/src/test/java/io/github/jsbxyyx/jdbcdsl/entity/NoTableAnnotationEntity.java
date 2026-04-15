package io.github.jsbxyyx.jdbcdsl.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * Test entity with NO {@code @Table} annotation — used to verify that the active
 * {@link io.github.jsbxyyx.jdbcdsl.NamingStrategy} is applied to the class name
 * when deriving the table name.
 *
 * <p>With the default strategy: table = {@code NoTableAnnotationEntity}<br>
 * With snake_case strategy:    table = {@code no_table_annotation_entity}
 */
@Entity
public class NoTableAnnotationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String displayName;

    public NoTableAnnotationEntity() {}

    public Long getId() { return id; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
}
