package io.github.jsbxyyx.jpadsl.core;

import io.github.jsbxyyx.jpadsl.example.entity.User_;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

class PageRequestBuilderTest {

    @Test
    void testDefaultValues() {
        Pageable pageable = PageRequestBuilder.builder().build();
        assertThat(pageable.getPageNumber()).isEqualTo(0);
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().isUnsorted()).isTrue();
    }

    @Test
    void testPageAndSize() {
        Pageable pageable = PageRequestBuilder.builder()
                .page(2)
                .size(20)
                .build();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(20);
    }

    @Test
    void testSortByMetamodelAttribute() {
        Pageable pageable = PageRequestBuilder.builder()
                .sortBy(User_.name, Sort.Direction.ASC)
                .build();
        assertThat(pageable.getSort().isSorted()).isTrue();
        assertThat(pageable.getSort().getOrderFor("name")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("name").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void testMultipleSorts() {
        Pageable pageable = PageRequestBuilder.builder()
                .page(0)
                .size(10)
                .sortBy(User_.age, Sort.Direction.DESC)
                .sortBy(User_.name, Sort.Direction.ASC)
                .build();
        assertThat(pageable.getSort().isSorted()).isTrue();
        assertThat(pageable.getSort().getOrderFor("age")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("name")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("age").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(pageable.getSort().getOrderFor("name").getDirection()).isEqualTo(Sort.Direction.ASC);
    }
}
