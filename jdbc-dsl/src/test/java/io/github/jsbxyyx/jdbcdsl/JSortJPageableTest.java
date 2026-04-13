package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.dto.UserDto;
import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests verifying that {@link JSort} and {@link JPageable} mirror Spring's
 * {@code Sort} / {@code PageRequest} API (with {@link SFunction} instead of {@code String}).
 */
class JSortJPageableTest {

    // ====================================================================
    //  JOrder
    // ====================================================================

    @Test
    void jOrder_asc_isAscending() {
        JOrder<TUser> o = JOrder.asc(TUser::getUsername);
        assertThat(o.isAscending()).isTrue();
        assertThat(o.isDescending()).isFalse();
        assertThat(o.getDirection()).isEqualTo(JOrder.Direction.ASC);
        assertThat(o.getPropertyRef().propertyName()).isEqualTo("username");
    }

    @Test
    void jOrder_desc_isDescending() {
        JOrder<TUser> o = JOrder.desc(TUser::getUsername);
        assertThat(o.isDescending()).isTrue();
        assertThat(o.isAscending()).isFalse();
        assertThat(o.getDirection()).isEqualTo(JOrder.Direction.DESC);
    }

    @Test
    void jOrder_by_defaultsToAsc() {
        JOrder<TUser> o = JOrder.by(TUser::getUsername);
        assertThat(o.isAscending()).isTrue();
    }

    @Test
    void jOrder_by_withDirection() {
        JOrder<TUser> o = JOrder.by(JOrder.Direction.DESC, TUser::getAge);
        assertThat(o.isDescending()).isTrue();
        assertThat(o.getPropertyRef().propertyName()).isEqualTo("age");
    }

    @Test
    void jOrder_reverse() {
        JOrder<TUser> asc = JOrder.asc(TUser::getUsername);
        JOrder<TUser> desc = asc.reverse();
        assertThat(desc.isDescending()).isTrue();
        assertThat(desc.reverse().isAscending()).isTrue();
    }

    @Test
    void jOrder_withDirection() {
        JOrder<TUser> o = JOrder.asc(TUser::getUsername).with(JOrder.Direction.DESC);
        assertThat(o.isDescending()).isTrue();
    }

    @Test
    void jOrder_withProperty() {
        JOrder<TUser> o = JOrder.asc(TUser::getUsername).withProperty(TUser::getEmail);
        assertThat(o.getPropertyRef().propertyName()).isEqualTo("email");
        assertThat(o.isAscending()).isTrue();
    }

    @Test
    void jOrder_ignoreCase() {
        JOrder<TUser> o = JOrder.asc(TUser::getUsername);
        assertThat(o.isIgnoreCase()).isFalse();
        JOrder<TUser> ci = o.ignoreCase();
        assertThat(ci.isIgnoreCase()).isTrue();
        assertThat(ci.getPropertyRef().propertyName()).isEqualTo("username");
    }

    @Test
    void jOrder_nullHandling_default_isNative() {
        JOrder<TUser> o = JOrder.asc(TUser::getUsername);
        assertThat(o.getNullHandling()).isEqualTo(JOrder.NullHandling.NATIVE);
    }

    @Test
    void jOrder_nullsFirst() {
        JOrder<TUser> o = JOrder.asc(TUser::getUsername).nullsFirst();
        assertThat(o.getNullHandling()).isEqualTo(JOrder.NullHandling.NULLS_FIRST);
    }

    @Test
    void jOrder_nullsLast() {
        JOrder<TUser> o = JOrder.asc(TUser::getUsername).nullsLast();
        assertThat(o.getNullHandling()).isEqualTo(JOrder.NullHandling.NULLS_LAST);
    }

    @Test
    void jOrder_nullsNative() {
        JOrder<TUser> o = JOrder.asc(TUser::getUsername).nullsFirst().nullsNative();
        assertThat(o.getNullHandling()).isEqualTo(JOrder.NullHandling.NATIVE);
    }

    @Test
    void jOrder_with_nullHandling() {
        JOrder<TUser> o = JOrder.asc(TUser::getUsername).with(JOrder.NullHandling.NULLS_LAST);
        assertThat(o.getNullHandling()).isEqualTo(JOrder.NullHandling.NULLS_LAST);
    }

    // ====================================================================
    //  JSort — factories
    // ====================================================================

    @Test
    void jSort_unsorted_isEmpty() {
        JSort<TUser> s = JSort.unsorted();
        assertThat(s.isUnsorted()).isTrue();
        assertThat(s.isSorted()).isFalse();
        assertThat(s.isEmpty()).isTrue();
        assertThat(s.getOrders()).isEmpty();
    }

    @Test
    void jSort_by_singleProp_defaultsToAsc() {
        JSort<TUser> s = JSort.by(TUser::getUsername);
        assertThat(s.getOrders()).hasSize(1);
        assertThat(s.getOrders().get(0).isAscending()).isTrue();
        assertThat(s.getOrders().get(0).getPropertyRef().propertyName()).isEqualTo("username");
    }

    @Test
    void jSort_by_multipleProps_allAsc() {
        JSort<TUser> s = JSort.by(TUser::getUsername, TUser::getAge);
        assertThat(s.getOrders()).hasSize(2);
        assertThat(s.getOrders()).allMatch(JOrder::isAscending);
    }

    @Test
    void jSort_by_direction_multipleProps() {
        JSort<TUser> s = JSort.by(JOrder.Direction.DESC, TUser::getUsername, TUser::getAge);
        assertThat(s.getOrders()).hasSize(2);
        assertThat(s.getOrders()).allMatch(JOrder::isDescending);
    }

    @Test
    void jSort_by_listOfOrders() {
        List<JOrder<TUser>> orders = List.of(JOrder.asc(TUser::getUsername), JOrder.desc(TUser::getAge));
        JSort<TUser> s = JSort.by(orders);
        assertThat(s.getOrders()).hasSize(2);
        assertThat(s.getOrders().get(0).isAscending()).isTrue();
        assertThat(s.getOrders().get(1).isDescending()).isTrue();
    }

    @Test
    void jSort_by_varargOrders() {
        JSort<TUser> s = JSort.by(JOrder.asc(TUser::getUsername), JOrder.desc(TUser::getAge));
        assertThat(s.getOrders()).hasSize(2);
    }

    // ====================================================================
    //  JSort — instance methods (Spring equivalents)
    // ====================================================================

    @Test
    void jSort_and_combinesOrders() {
        JSort<TUser> s1 = JSort.byAsc(TUser::getUsername);
        JSort<TUser> s2 = JSort.byDesc(TUser::getAge);
        JSort<TUser> combined = s1.and(s2);
        assertThat(combined.getOrders()).hasSize(2);
        assertThat(combined.getOrders().get(0).getPropertyRef().propertyName()).isEqualTo("username");
        assertThat(combined.getOrders().get(1).getPropertyRef().propertyName()).isEqualTo("age");
    }

    @Test
    void jSort_ascending_switchesAllToAsc() {
        JSort<TUser> s = JSort.byDesc(TUser::getUsername).andDesc(TUser::getAge);
        JSort<TUser> asc = s.ascending();
        assertThat(asc.getOrders()).allMatch(JOrder::isAscending);
    }

    @Test
    void jSort_descending_switchesAllToDesc() {
        JSort<TUser> s = JSort.byAsc(TUser::getUsername).andAsc(TUser::getAge);
        JSort<TUser> desc = s.descending();
        assertThat(desc.getOrders()).allMatch(JOrder::isDescending);
    }

    @Test
    void jSort_reverse_reversesDirections() {
        JSort<TUser> s = JSort.byAsc(TUser::getUsername).andDesc(TUser::getAge);
        JSort<TUser> reversed = s.reverse();
        assertThat(reversed.getOrders().get(0).isDescending()).isTrue();
        assertThat(reversed.getOrders().get(1).isAscending()).isTrue();
    }

    @Test
    void jSort_isSorted_isUnsorted() {
        assertThat(JSort.byAsc(TUser::getUsername).isSorted()).isTrue();
        assertThat(JSort.byAsc(TUser::getUsername).isUnsorted()).isFalse();
        assertThat(JSort.unsorted().isSorted()).isFalse();
        assertThat(JSort.unsorted().isUnsorted()).isTrue();
    }

    @Test
    void jSort_getOrderFor_found() {
        JSort<TUser> s = JSort.byAsc(TUser::getUsername).andDesc(TUser::getAge);
        Optional<JOrder<TUser>> order = s.getOrderFor(TUser::getUsername);
        assertThat(order).isPresent();
        assertThat(order.get().isAscending()).isTrue();
    }

    @Test
    void jSort_getOrderFor_notFound() {
        JSort<TUser> s = JSort.byAsc(TUser::getUsername);
        Optional<JOrder<TUser>> order = s.getOrderFor(TUser::getAge);
        assertThat(order).isEmpty();
    }

    @Test
    void jSort_iterator_iteratesOrders() {
        JSort<TUser> s = JSort.byAsc(TUser::getUsername).andDesc(TUser::getAge);
        int count = 0;
        for (JOrder<TUser> o : s) {
            count++;
        }
        assertThat(count).isEqualTo(2);
    }

    @Test
    void jSort_stream_worksCorrectly() {
        JSort<TUser> s = JSort.byAsc(TUser::getUsername).andDesc(TUser::getAge);
        long ascCount = s.stream().filter(JOrder::isAscending).count();
        assertThat(ascCount).isEqualTo(1);
    }

    @Test
    void jSort_filter_returnsFiltered() {
        JSort<TUser> s = JSort.byAsc(TUser::getUsername).andDesc(TUser::getAge);
        JSort<TUser> ascOnly = s.filter(JOrder::isAscending);
        assertThat(ascOnly.getOrders()).hasSize(1);
        assertThat(ascOnly.getOrders().get(0).getPropertyRef().propertyName()).isEqualTo("username");
    }

    // ====================================================================
    //  JSort — toSpringSort (output only)
    // ====================================================================

    @Test
    void jSort_toSpringSort_unsorted() {
        Sort springSort = JSort.unsorted().toSpringSort();
        assertThat(springSort.isUnsorted()).isTrue();
    }

    @Test
    void jSort_toSpringSort_withOrders() {
        Sort springSort = JSort.byAsc(TUser::getUsername).andDesc(TUser::getAge).toSpringSort();
        assertThat(springSort.isSorted()).isTrue();
        assertThat(springSort.getOrderFor("username")).isNotNull();
        assertThat(springSort.getOrderFor("username").isAscending()).isTrue();
        assertThat(springSort.getOrderFor("age").isDescending()).isTrue();
    }

    @Test
    void jSort_toSpringSort_propagatesIgnoreCase() {
        Sort springSort = JSort.by(JOrder.asc(TUser::getUsername).ignoreCase()).toSpringSort();
        assertThat(springSort.getOrderFor("username").isIgnoreCase()).isTrue();
    }

    @Test
    void jSort_toSpringSort_propagatesNullHandling() {
        Sort springSort = JSort.by(JOrder.asc(TUser::getUsername).nullsFirst()).toSpringSort();
        assertThat(springSort.getOrderFor("username").getNullHandling())
                .isEqualTo(Sort.NullHandling.NULLS_FIRST);
    }

    // ====================================================================
    //  JSort — SQL rendering with ignoreCase / NullHandling
    // ====================================================================

    @Test
    void sqlRenderer_orderBy_ignoreCase() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .orderBy(JSort.by(JOrder.asc(TUser::getUsername).ignoreCase()))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("LOWER(t.username) ASC");
    }

    @Test
    void sqlRenderer_orderBy_nullsFirst() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .orderBy(JSort.by(JOrder.asc(TUser::getUsername).nullsFirst()))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("NULLS FIRST");
    }

    @Test
    void sqlRenderer_orderBy_nullsLast() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .orderBy(JSort.by(JOrder.desc(TUser::getUsername).nullsLast()))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        assertThat(rendered.getSql()).contains("NULLS LAST");
    }

    // ====================================================================
    //  JPageable — factories
    // ====================================================================

    @Test
    void jPageable_of_pageAndSize() {
        JPageable<TUser> p = JPageable.of(2, 10);
        assertThat(p.getPageNumber()).isEqualTo(2);
        assertThat(p.getPageSize()).isEqualTo(10);
        assertThat(p.getSort().isUnsorted()).isTrue();
        assertThat(p.getOffset()).isEqualTo(20L);
    }

    @Test
    void jPageable_of_withSort() {
        JSort<TUser> sort = JSort.byAsc(TUser::getUsername);
        JPageable<TUser> p = JPageable.of(1, 5, sort);
        assertThat(p.getSort().isSorted()).isTrue();
    }

    @Test
    void jPageable_of_withDirectionAndProps() {
        JPageable<TUser> p = JPageable.of(0, 10, JOrder.Direction.DESC, TUser::getUsername, TUser::getAge);
        assertThat(p.getSort().getOrders()).hasSize(2);
        assertThat(p.getSort().getOrders()).allMatch(JOrder::isDescending);
    }

    @Test
    void jPageable_ofSize_createsFirstPage() {
        JPageable<TUser> p = JPageable.ofSize(15);
        assertThat(p.getPageNumber()).isEqualTo(0);
        assertThat(p.getPageSize()).isEqualTo(15);
        assertThat(p.getSort().isUnsorted()).isTrue();
    }

    @Test
    void jPageable_negativePage_throws() {
        assertThatThrownBy(() -> JPageable.of(-1, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void jPageable_zeroSize_throws() {
        assertThatThrownBy(() -> JPageable.of(0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    // ====================================================================
    //  JPageable — accessor aliases
    // ====================================================================

    @Test
    void jPageable_getPage_and_getPageNumber_match() {
        JPageable<TUser> p = JPageable.of(3, 10);
        assertThat(p.getPage()).isEqualTo(p.getPageNumber()).isEqualTo(3);
    }

    @Test
    void jPageable_getSize_and_getPageSize_match() {
        JPageable<TUser> p = JPageable.of(0, 7);
        assertThat(p.getSize()).isEqualTo(p.getPageSize()).isEqualTo(7);
    }

    @Test
    void jPageable_offset_and_getOffset_match() {
        JPageable<TUser> p = JPageable.of(3, 10);
        assertThat(p.offset()).isEqualTo(p.getOffset()).isEqualTo(30L);
    }

    // ====================================================================
    //  JPageable — navigation
    // ====================================================================

    @Test
    void jPageable_next_incrementsPage() {
        JPageable<TUser> p = JPageable.of(1, 10);
        JPageable<TUser> next = p.next();
        assertThat(next.getPageNumber()).isEqualTo(2);
        assertThat(next.getPageSize()).isEqualTo(10);
    }

    @Test
    void jPageable_previous_decrementPage() {
        JPageable<TUser> p = JPageable.of(2, 10);
        JPageable<TUser> prev = p.previous();
        assertThat(prev.getPageNumber()).isEqualTo(1);
    }

    @Test
    void jPageable_previous_onFirstPage_throws() {
        JPageable<TUser> p = JPageable.of(0, 10);
        assertThatThrownBy(p::previous)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("previousOrFirst");
    }

    @Test
    void jPageable_previousOrFirst_onFirstPage_returnsSelf() {
        JPageable<TUser> p = JPageable.of(0, 10);
        JPageable<TUser> result = p.previousOrFirst();
        assertThat(result.getPageNumber()).isEqualTo(0);
    }

    @Test
    void jPageable_previousOrFirst_onLaterPage_decrementsPage() {
        JPageable<TUser> p = JPageable.of(3, 10);
        JPageable<TUser> result = p.previousOrFirst();
        assertThat(result.getPageNumber()).isEqualTo(2);
    }

    @Test
    void jPageable_first_returnsPageZero() {
        JPageable<TUser> p = JPageable.of(5, 10);
        JPageable<TUser> first = p.first();
        assertThat(first.getPageNumber()).isEqualTo(0);
        assertThat(first.getPageSize()).isEqualTo(10);
    }

    @Test
    void jPageable_first_onFirstPage_returnsSelf() {
        JPageable<TUser> p = JPageable.of(0, 10);
        assertThat(p.first()).isSameAs(p);
    }

    @Test
    void jPageable_withPage_createsNewPageable() {
        JPageable<TUser> p = JPageable.of(0, 10);
        JPageable<TUser> p5 = p.withPage(5);
        assertThat(p5.getPageNumber()).isEqualTo(5);
        assertThat(p5.getPageSize()).isEqualTo(10);
    }

    @Test
    void jPageable_withPage_samePage_returnsSelf() {
        JPageable<TUser> p = JPageable.of(2, 10);
        assertThat(p.withPage(2)).isSameAs(p);
    }

    @Test
    void jPageable_hasPrevious() {
        assertThat(JPageable.of(0, 10).hasPrevious()).isFalse();
        assertThat(JPageable.of(1, 10).hasPrevious()).isTrue();
    }

    @Test
    void jPageable_isPaged_isUnpaged() {
        JPageable<TUser> p = JPageable.of(0, 10);
        assertThat(p.isPaged()).isTrue();
        assertThat(p.isUnpaged()).isFalse();
    }

    // ====================================================================
    //  JPageable — getSortOr
    // ====================================================================

    @Test
    void jPageable_getSortOr_unsorted_returnsDefault() {
        JPageable<TUser> p = JPageable.of(0, 10);
        JSort<TUser> defaultSort = JSort.byAsc(TUser::getUsername);
        assertThat(p.getSortOr(defaultSort)).isSameAs(defaultSort);
    }

    @Test
    void jPageable_getSortOr_withSort_returnsOwn() {
        JSort<TUser> mySort = JSort.byDesc(TUser::getAge);
        JPageable<TUser> p = JPageable.of(0, 10, mySort);
        JSort<TUser> defaultSort = JSort.byAsc(TUser::getUsername);
        assertThat(p.getSortOr(defaultSort)).isSameAs(mySort);
    }

    // ====================================================================
    //  JPageable — withSort
    // ====================================================================

    @Test
    void jPageable_withSort_jsort() {
        JPageable<TUser> p = JPageable.of(1, 10);
        JSort<TUser> newSort = JSort.byDesc(TUser::getAge);
        JPageable<TUser> updated = p.withSort(newSort);
        assertThat(updated.getPageNumber()).isEqualTo(1);
        assertThat(updated.getSort()).isSameAs(newSort);
    }

    @Test
    void jPageable_withSort_directionAndProps() {
        JPageable<TUser> p = JPageable.of(0, 10);
        JPageable<TUser> updated = p.withSort(JOrder.Direction.DESC, TUser::getUsername);
        assertThat(updated.getSort().getOrders().get(0).isDescending()).isTrue();
    }

    // ====================================================================
    //  JPageable — toSpringPageable (output only)
    // ====================================================================

    @Test
    void jPageable_toSpringPageable_correctValues() {
        JPageable<TUser> p = JPageable.of(2, 5, JSort.byAsc(TUser::getUsername));
        org.springframework.data.domain.Pageable spring = p.toSpringPageable();
        assertThat(spring.getPageNumber()).isEqualTo(2);
        assertThat(spring.getPageSize()).isEqualTo(5);
        assertThat(spring.getSort().isSorted()).isTrue();
    }
}
