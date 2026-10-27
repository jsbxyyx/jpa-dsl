package io.github.jsbxyyx.jpadsl.core;

import io.github.jsbxyyx.jpadsl.example.entity.User_;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PageRequestBuilderTest {

    @Test
    void testDefaultValues() {
        Pageable pageable = PageRequestBuilder.builder().build();
        org.junit.jupiter.api.Assertions.assertEquals(0, pageable.getPageNumber());
        org.junit.jupiter.api.Assertions.assertEquals(20, pageable.getPageSize());
        org.junit.jupiter.api.Assertions.assertTrue(pageable.getSort().isUnsorted());
    }

    @Test
    void testPageAndSize() {
        Pageable pageable = PageRequestBuilder.builder().page(2).size(20).build();
        org.junit.jupiter.api.Assertions.assertEquals(2, pageable.getPageNumber());
        org.junit.jupiter.api.Assertions.assertEquals(20, pageable.getPageSize());
    }

    @Test
    void testSortByMetamodelAttribute() {
        Pageable pageable = PageRequestBuilder.builder()
                .sortBy(User_.name, Sort.Direction.ASC)
                .build();
        org.junit.jupiter.api.Assertions.assertTrue(pageable.getSort().isSorted());
        org.junit.jupiter.api.Assertions.assertNotNull(pageable.getSort().getOrderFor("name"));
        org.junit.jupiter.api.Assertions.assertEquals(
                Sort.Direction.ASC, pageable.getSort().getOrderFor("name").getDirection());
    }

    @Test
    void testMultipleSorts() {
        Pageable pageable = PageRequestBuilder.builder()
                .page(0)
                .size(10)
                .sortBy(User_.age, Sort.Direction.DESC)
                .sortBy(User_.name, Sort.Direction.ASC)
                .build();
        org.junit.jupiter.api.Assertions.assertTrue(pageable.getSort().isSorted());
        org.junit.jupiter.api.Assertions.assertNotNull(pageable.getSort().getOrderFor("age"));
        org.junit.jupiter.api.Assertions.assertNotNull(pageable.getSort().getOrderFor("name"));
        org.junit.jupiter.api.Assertions.assertEquals(
                Sort.Direction.DESC, pageable.getSort().getOrderFor("age").getDirection());
        org.junit.jupiter.api.Assertions.assertEquals(
                Sort.Direction.ASC, pageable.getSort().getOrderFor("name").getDirection());
    }
}
