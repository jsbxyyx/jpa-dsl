package io.github.jsbxyyx.jpadsl.core;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

class PageRequestBuilderTest {

    @Test
    void testDefaultValues() {
        PageRequest pageRequest = PageRequestBuilder.builder().build();
        assertThat(pageRequest.getPageNumber()).isEqualTo(0);
        assertThat(pageRequest.getPageSize()).isEqualTo(10);
        assertThat(pageRequest.getSort().isUnsorted()).isTrue();
    }

    @Test
    void testPageAndSize() {
        PageRequest pageRequest = PageRequestBuilder.builder()
                .page(2)
                .size(20)
                .build();
        assertThat(pageRequest.getPageNumber()).isEqualTo(2);
        assertThat(pageRequest.getPageSize()).isEqualTo(20);
    }

    @Test
    void testSingleSort() {
        PageRequest pageRequest = PageRequestBuilder.builder()
                .sortBy("name", Sort.Direction.ASC)
                .build();
        assertThat(pageRequest.getSort().isSorted()).isTrue();
        assertThat(pageRequest.getSort().getOrderFor("name")).isNotNull();
        assertThat(pageRequest.getSort().getOrderFor("name").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void testMultipleSorts() {
        PageRequest pageRequest = PageRequestBuilder.builder()
                .page(0)
                .size(10)
                .sortBy("createdAt", Sort.Direction.DESC)
                .sortBy("name", Sort.Direction.ASC)
                .build();
        assertThat(pageRequest.getSort().isSorted()).isTrue();
        assertThat(pageRequest.getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(pageRequest.getSort().getOrderFor("name")).isNotNull();
        assertThat(pageRequest.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(pageRequest.getSort().getOrderFor("name").getDirection()).isEqualTo(Sort.Direction.ASC);
    }
}
