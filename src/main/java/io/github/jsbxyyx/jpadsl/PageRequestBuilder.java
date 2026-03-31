package io.github.jsbxyyx.jpadsl;

import jakarta.persistence.metamodel.SingularAttribute;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for constructing Spring Data {@link Pageable} instances.
 *
 * <p>Supports both string-based sort properties (for backward compatibility) and
 * type-safe sort using JPA Static Metamodel attributes via {@link #sortBy}.
 *
 * <p>Example:
 * <pre>{@code
 * Pageable pageable = PageRequestBuilder.builder()
 *     .page(0)
 *     .size(20)
 *     .sortBy(User_.lastName, Sort.Direction.ASC)
 *     .sortBy(User_.createdAt, Sort.Direction.DESC)
 *     .build();
 * }</pre>
 */
public class PageRequestBuilder {

    private int page = 0;
    private int size = 20;
    private final List<Sort.Order> orders = new ArrayList<>();

    private PageRequestBuilder() {
    }

    public static PageRequestBuilder builder() {
        return new PageRequestBuilder();
    }

    public PageRequestBuilder page(int page) {
        if (page < 0) {
            throw new IllegalArgumentException("Page index must not be less than zero");
        }
        this.page = page;
        return this;
    }

    public PageRequestBuilder size(int size) {
        if (size < 1) {
            throw new IllegalArgumentException("Page size must not be less than one");
        }
        this.size = size;
        return this;
    }

    /**
     * Adds a sort order using a type-safe JPA Static Metamodel attribute.
     *
     * @param attr      the metamodel attribute to sort by (e.g. {@code User_.name})
     * @param direction sort direction
     */
    public PageRequestBuilder sortBy(SingularAttribute<?, ?> attr, Sort.Direction direction) {
        orders.add(new Sort.Order(direction, attr.getName()));
        return this;
    }

    public PageRequestBuilder asc(String... properties) {
        for (String property : properties) {
            orders.add(Sort.Order.asc(property));
        }
        return this;
    }

    public PageRequestBuilder desc(String... properties) {
        for (String property : properties) {
            orders.add(Sort.Order.desc(property));
        }
        return this;
    }

    public PageRequestBuilder sort(Sort.Order... sortOrders) {
        for (Sort.Order order : sortOrders) {
            orders.add(order);
        }
        return this;
    }

    public Pageable build() {
        if (orders.isEmpty()) {
            return PageRequest.of(page, size);
        }
        return PageRequest.of(page, size, Sort.by(orders));
    }
}
