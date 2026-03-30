package io.github.jsbxyyx.jpadsl;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageRequestBuilderTest {

    @Test
    void defaultPageable_shouldHaveDefaultValues() {
        Pageable pageable = PageRequestBuilder.builder().build();
        assertThat(pageable.getPageNumber()).isEqualTo(0);
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().isUnsorted()).isTrue();
    }

    @Test
    void customPageAndSize() {
        Pageable pageable = PageRequestBuilder.builder()
                .page(2)
                .size(10)
                .build();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(10);
    }

    @Test
    void ascSorting() {
        Pageable pageable = PageRequestBuilder.builder()
                .asc("name")
                .build();
        Sort.Order order = pageable.getSort().getOrderFor("name");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void descSorting() {
        Pageable pageable = PageRequestBuilder.builder()
                .desc("createdAt")
                .build();
        Sort.Order order = pageable.getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void multipleSortOrders() {
        Pageable pageable = PageRequestBuilder.builder()
                .asc("lastName")
                .desc("createdAt")
                .build();
        assertThat(pageable.getSort().getOrderFor("lastName").getDirection())
                .isEqualTo(Sort.Direction.ASC);
        assertThat(pageable.getSort().getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void negativePage_shouldThrow() {
        assertThatThrownBy(() -> PageRequestBuilder.builder().page(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Page index must not be less than zero");
    }

    @Test
    void zeroSize_shouldThrow() {
        assertThatThrownBy(() -> PageRequestBuilder.builder().size(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Page size must not be less than one");
    }

    @Test
    void sortWithOrderObject() {
        Pageable pageable = PageRequestBuilder.builder()
                .sort(Sort.Order.asc("name"), Sort.Order.desc("age"))
                .build();
        assertThat(pageable.getSort().getOrderFor("name").getDirection())
                .isEqualTo(Sort.Direction.ASC);
        assertThat(pageable.getSort().getOrderFor("age").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }
}
