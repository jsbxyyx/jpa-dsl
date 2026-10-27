package io.github.jsbxyyx.jpadsl;

import io.github.jsbxyyx.jpadsl.testmodel.User_;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PageRequestBuilderTest {

    @Test
    void defaultPageable_shouldHaveDefaultValues() {
        Pageable pageable = PageRequestBuilder.builder().build();
        org.junit.jupiter.api.Assertions.assertEquals(0, pageable.getPageNumber());
        org.junit.jupiter.api.Assertions.assertEquals(20, pageable.getPageSize());
        org.junit.jupiter.api.Assertions.assertTrue(pageable.getSort().isUnsorted());
    }

    @Test
    void customPageAndSize() {
        Pageable pageable = PageRequestBuilder.builder().page(2).size(10).build();
        org.junit.jupiter.api.Assertions.assertEquals(2, pageable.getPageNumber());
        org.junit.jupiter.api.Assertions.assertEquals(10, pageable.getPageSize());
    }

    @Test
    void ascSorting() {
        Pageable pageable = PageRequestBuilder.builder().asc("name").build();
        Sort.Order order = pageable.getSort().getOrderFor("name");
        org.junit.jupiter.api.Assertions.assertNotNull(order);
        org.junit.jupiter.api.Assertions.assertEquals(Sort.Direction.ASC, order.getDirection());
    }

    @Test
    void descSorting() {
        Pageable pageable = PageRequestBuilder.builder().desc("createdAt").build();
        Sort.Order order = pageable.getSort().getOrderFor("createdAt");
        org.junit.jupiter.api.Assertions.assertNotNull(order);
        org.junit.jupiter.api.Assertions.assertEquals(Sort.Direction.DESC, order.getDirection());
    }

    @Test
    void multipleSortOrders() {
        Pageable pageable =
                PageRequestBuilder.builder().asc("lastName").desc("createdAt").build();
        org.junit.jupiter.api.Assertions.assertEquals(
                Sort.Direction.ASC, pageable.getSort().getOrderFor("lastName").getDirection());
        org.junit.jupiter.api.Assertions.assertEquals(
                Sort.Direction.DESC, pageable.getSort().getOrderFor("createdAt").getDirection());
    }

    @Test
    void negativePage_shouldThrow() {
        Throwable assertionThrown4 = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> PageRequestBuilder.builder().page(-1).build());
        org.junit.jupiter.api.Assertions.assertTrue(
                assertionThrown4.getMessage().contains("Page index must not be less than zero"));
    }

    @Test
    void zeroSize_shouldThrow() {
        Throwable assertionThrown5 = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> PageRequestBuilder.builder().size(0).build());
        org.junit.jupiter.api.Assertions.assertTrue(
                assertionThrown5.getMessage().contains("Page size must not be less than one"));
    }

    @Test
    void sortWithOrderObject() {
        Pageable pageable = PageRequestBuilder.builder()
                .sort(Sort.Order.asc("name"), Sort.Order.desc("age"))
                .build();
        org.junit.jupiter.api.Assertions.assertEquals(
                Sort.Direction.ASC, pageable.getSort().getOrderFor("name").getDirection());
        org.junit.jupiter.api.Assertions.assertEquals(
                Sort.Direction.DESC, pageable.getSort().getOrderFor("age").getDirection());
    }

    @Test
    void sortBy_metamodelAttribute_shouldUseAttributeName() {
        Pageable pageable = PageRequestBuilder.builder()
                .page(0)
                .size(10)
                .sortBy(User_.name, Sort.Direction.ASC)
                .sortBy(User_.age, Sort.Direction.DESC)
                .build();
        org.junit.jupiter.api.Assertions.assertEquals(0, pageable.getPageNumber());
        org.junit.jupiter.api.Assertions.assertEquals(10, pageable.getPageSize());
        Sort.Order nameOrder = pageable.getSort().getOrderFor("name");
        org.junit.jupiter.api.Assertions.assertNotNull(nameOrder);
        org.junit.jupiter.api.Assertions.assertEquals(Sort.Direction.ASC, nameOrder.getDirection());
        Sort.Order ageOrder = pageable.getSort().getOrderFor("age");
        org.junit.jupiter.api.Assertions.assertNotNull(ageOrder);
        org.junit.jupiter.api.Assertions.assertEquals(Sort.Direction.DESC, ageOrder.getDirection());
    }
}
