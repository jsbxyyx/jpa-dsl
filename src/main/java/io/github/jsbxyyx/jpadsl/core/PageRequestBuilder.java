package io.github.jsbxyyx.jpadsl.core;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for constructing Spring Data PageRequest instances.
 *
 * <p>Usage example:
 * <pre>{@code
 * PageRequest pageRequest = PageRequestBuilder.builder()
 *     .page(0)
 *     .size(20)
 *     .sortBy("createdAt", Sort.Direction.DESC)
 *     .sortBy("name", Sort.Direction.ASC)
 *     .build();
 * }</pre>
 */
public class PageRequestBuilder {

    private int page = 0;
    private int size = 10;
    private final List<Sort.Order> orders = new ArrayList<>();

    private PageRequestBuilder() {}

    public static PageRequestBuilder builder() {
        return new PageRequestBuilder();
    }

    public PageRequestBuilder page(int page) {
        this.page = page;
        return this;
    }

    public PageRequestBuilder size(int size) {
        this.size = size;
        return this;
    }

    public PageRequestBuilder sortBy(String field, Sort.Direction direction) {
        orders.add(new Sort.Order(direction, field));
        return this;
    }

    public PageRequest build() {
        if (orders.isEmpty()) {
            return PageRequest.of(page, size);
        }
        return PageRequest.of(page, size, Sort.by(orders));
    }
}
