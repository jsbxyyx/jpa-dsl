package io.github.jsbxyyx.jpadsl;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for constructing Spring Data {@link Pageable} instances.
 *
 * <p>Example:
 * <pre>{@code
 * Pageable pageable = PageRequestBuilder.builder()
 *     .page(0)
 *     .size(20)
 *     .asc("lastName")
 *     .desc("createdAt")
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
