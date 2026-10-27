package io.github.jsbxyyx.jdbcdsl;

import io.github.jsbxyyx.jdbcdsl.dto.UserDto;
import io.github.jsbxyyx.jdbcdsl.entity.TUser;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

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
        org.junit.jupiter.api.Assertions.assertTrue(o.isAscending());
        org.junit.jupiter.api.Assertions.assertFalse(o.isDescending());
        org.junit.jupiter.api.Assertions.assertEquals(JOrder.Direction.ASC, o.getDirection());
        org.junit.jupiter.api.Assertions.assertEquals(
                "username", o.getPropertyRef().propertyName());
    }

    @Test
    void jOrder_desc_isDescending() {
        JOrder<TUser> o = JOrder.desc(TUser::getUsername);
        org.junit.jupiter.api.Assertions.assertTrue(o.isDescending());
        org.junit.jupiter.api.Assertions.assertFalse(o.isAscending());
        org.junit.jupiter.api.Assertions.assertEquals(JOrder.Direction.DESC, o.getDirection());
    }

    @Test
    void jOrder_by_defaultsToAsc() {
        JOrder<TUser> o = JOrder.by(TUser::getUsername);
        org.junit.jupiter.api.Assertions.assertTrue(o.isAscending());
    }

    @Test
    void jOrder_by_withDirection() {
        JOrder<TUser> o = JOrder.by(JOrder.Direction.DESC, TUser::getAge);
        org.junit.jupiter.api.Assertions.assertTrue(o.isDescending());
        org.junit.jupiter.api.Assertions.assertEquals("age", o.getPropertyRef().propertyName());
    }

    @Test
    void jOrder_reverse() {
        JOrder<TUser> asc = JOrder.asc(TUser::getUsername);
        JOrder<TUser> desc = asc.reverse();
        org.junit.jupiter.api.Assertions.assertTrue(desc.isDescending());
        org.junit.jupiter.api.Assertions.assertTrue(desc.reverse().isAscending());
    }

    @Test
    void jOrder_withDirection() {
        JOrder<TUser> o = JOrder.asc(TUser::getUsername).with(JOrder.Direction.DESC);
        org.junit.jupiter.api.Assertions.assertTrue(o.isDescending());
    }

    @Test
    void jOrder_withProperty() {
        JOrder<TUser> o = JOrder.asc(TUser::getUsername).withProperty(TUser::getEmail);
        org.junit.jupiter.api.Assertions.assertEquals(
                "email", o.getPropertyRef().propertyName());
        org.junit.jupiter.api.Assertions.assertTrue(o.isAscending());
    }

    @Test
    void jOrder_ignoreCase() {
        JOrder<TUser> o = JOrder.asc(TUser::getUsername);
        org.junit.jupiter.api.Assertions.assertFalse(o.isIgnoreCase());
        JOrder<TUser> ci = o.ignoreCase();
        org.junit.jupiter.api.Assertions.assertTrue(ci.isIgnoreCase());
        org.junit.jupiter.api.Assertions.assertEquals(
                "username", ci.getPropertyRef().propertyName());
    }

    @Test
    void jOrder_nullHandling_default_isNative() {
        JOrder<TUser> o = JOrder.asc(TUser::getUsername);
        org.junit.jupiter.api.Assertions.assertEquals(JOrder.NullHandling.NATIVE, o.getNullHandling());
    }

    @Test
    void jOrder_nullsFirst() {
        JOrder<TUser> o = JOrder.asc(TUser::getUsername).nullsFirst();
        org.junit.jupiter.api.Assertions.assertEquals(JOrder.NullHandling.NULLS_FIRST, o.getNullHandling());
    }

    @Test
    void jOrder_nullsLast() {
        JOrder<TUser> o = JOrder.asc(TUser::getUsername).nullsLast();
        org.junit.jupiter.api.Assertions.assertEquals(JOrder.NullHandling.NULLS_LAST, o.getNullHandling());
    }

    @Test
    void jOrder_nullsNative() {
        JOrder<TUser> o = JOrder.asc(TUser::getUsername).nullsFirst().nullsNative();
        org.junit.jupiter.api.Assertions.assertEquals(JOrder.NullHandling.NATIVE, o.getNullHandling());
    }

    @Test
    void jOrder_with_nullHandling() {
        JOrder<TUser> o = JOrder.asc(TUser::getUsername).with(JOrder.NullHandling.NULLS_LAST);
        org.junit.jupiter.api.Assertions.assertEquals(JOrder.NullHandling.NULLS_LAST, o.getNullHandling());
    }

    // ====================================================================
    //  JSort — factories
    // ====================================================================

    @Test
    void jSort_unsorted_isEmpty() {
        JSort<TUser> s = JSort.unsorted();
        org.junit.jupiter.api.Assertions.assertTrue(s.isUnsorted());
        org.junit.jupiter.api.Assertions.assertFalse(s.isSorted());
        org.junit.jupiter.api.Assertions.assertTrue(s.isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(s.getOrders().isEmpty());
    }

    @Test
    void jSort_by_singleProp_defaultsToAsc() {
        JSort<TUser> s = JSort.by(TUser::getUsername);
        org.junit.jupiter.api.Assertions.assertEquals(1, s.getOrders().size());
        org.junit.jupiter.api.Assertions.assertTrue(s.getOrders().get(0).isAscending());
        org.junit.jupiter.api.Assertions.assertEquals(
                "username", s.getOrders().get(0).getPropertyRef().propertyName());
    }

    @Test
    void jSort_by_multipleProps_allAsc() {
        JSort<TUser> s = JSort.by(TUser::getUsername, TUser::getAge);
        org.junit.jupiter.api.Assertions.assertEquals(2, s.getOrders().size());
        org.junit.jupiter.api.Assertions.assertTrue(s.getOrders().stream().allMatch(JOrder::isAscending));
    }

    @Test
    void jSort_by_direction_multipleProps() {
        JSort<TUser> s = JSort.by(JOrder.Direction.DESC, TUser::getUsername, TUser::getAge);
        org.junit.jupiter.api.Assertions.assertEquals(2, s.getOrders().size());
        org.junit.jupiter.api.Assertions.assertTrue(s.getOrders().stream().allMatch(JOrder::isDescending));
    }

    @Test
    void jSort_by_listOfOrders() {
        List<JOrder<TUser>> orders = List.of(JOrder.asc(TUser::getUsername), JOrder.desc(TUser::getAge));
        JSort<TUser> s = JSort.by(orders);
        org.junit.jupiter.api.Assertions.assertEquals(2, s.getOrders().size());
        org.junit.jupiter.api.Assertions.assertTrue(s.getOrders().get(0).isAscending());
        org.junit.jupiter.api.Assertions.assertTrue(s.getOrders().get(1).isDescending());
    }

    @Test
    void jSort_by_varargOrders() {
        JSort<TUser> s = JSort.by(JOrder.asc(TUser::getUsername), JOrder.desc(TUser::getAge));
        org.junit.jupiter.api.Assertions.assertEquals(2, s.getOrders().size());
    }

    // ====================================================================
    //  JSort — instance methods (Spring equivalents)
    // ====================================================================

    @Test
    void jSort_and_combinesOrders() {
        JSort<TUser> s1 = JSort.byAsc(TUser::getUsername);
        JSort<TUser> s2 = JSort.byDesc(TUser::getAge);
        JSort<TUser> combined = s1.and(s2);
        org.junit.jupiter.api.Assertions.assertEquals(2, combined.getOrders().size());
        org.junit.jupiter.api.Assertions.assertEquals(
                "username", combined.getOrders().get(0).getPropertyRef().propertyName());
        org.junit.jupiter.api.Assertions.assertEquals(
                "age", combined.getOrders().get(1).getPropertyRef().propertyName());
    }

    @Test
    void jSort_ascending_switchesAllToAsc() {
        JSort<TUser> s = JSort.byDesc(TUser::getUsername).andDesc(TUser::getAge);
        JSort<TUser> asc = s.ascending();
        org.junit.jupiter.api.Assertions.assertTrue(asc.getOrders().stream().allMatch(JOrder::isAscending));
    }

    @Test
    void jSort_descending_switchesAllToDesc() {
        JSort<TUser> s = JSort.byAsc(TUser::getUsername).andAsc(TUser::getAge);
        JSort<TUser> desc = s.descending();
        org.junit.jupiter.api.Assertions.assertTrue(desc.getOrders().stream().allMatch(JOrder::isDescending));
    }

    @Test
    void jSort_reverse_reversesDirections() {
        JSort<TUser> s = JSort.byAsc(TUser::getUsername).andDesc(TUser::getAge);
        JSort<TUser> reversed = s.reverse();
        org.junit.jupiter.api.Assertions.assertTrue(reversed.getOrders().get(0).isDescending());
        org.junit.jupiter.api.Assertions.assertTrue(reversed.getOrders().get(1).isAscending());
    }

    @Test
    void jSort_isSorted_isUnsorted() {
        org.junit.jupiter.api.Assertions.assertTrue(
                JSort.byAsc(TUser::getUsername).isSorted());
        org.junit.jupiter.api.Assertions.assertFalse(
                JSort.byAsc(TUser::getUsername).isUnsorted());
        org.junit.jupiter.api.Assertions.assertFalse(JSort.unsorted().isSorted());
        org.junit.jupiter.api.Assertions.assertTrue(JSort.unsorted().isUnsorted());
    }

    @Test
    void jSort_getOrderFor_found() {
        JSort<TUser> s = JSort.byAsc(TUser::getUsername).andDesc(TUser::getAge);
        Optional<JOrder<TUser>> order = s.getOrderFor(TUser::getUsername);
        org.junit.jupiter.api.Assertions.assertTrue(order.isPresent());
        org.junit.jupiter.api.Assertions.assertTrue(order.get().isAscending());
    }

    @Test
    void jSort_getOrderFor_notFound() {
        JSort<TUser> s = JSort.byAsc(TUser::getUsername);
        Optional<JOrder<TUser>> order = s.getOrderFor(TUser::getAge);
        org.junit.jupiter.api.Assertions.assertTrue(order.isEmpty());
    }

    @Test
    void jSort_iterator_iteratesOrders() {
        JSort<TUser> s = JSort.byAsc(TUser::getUsername).andDesc(TUser::getAge);
        int count = 0;
        for (JOrder<TUser> o : s) {
            count++;
        }
        org.junit.jupiter.api.Assertions.assertEquals(2, count);
    }

    @Test
    void jSort_stream_worksCorrectly() {
        JSort<TUser> s = JSort.byAsc(TUser::getUsername).andDesc(TUser::getAge);
        long ascCount = s.stream().filter(JOrder::isAscending).count();
        org.junit.jupiter.api.Assertions.assertEquals(1, ascCount);
    }

    @Test
    void jSort_filter_returnsFiltered() {
        JSort<TUser> s = JSort.byAsc(TUser::getUsername).andDesc(TUser::getAge);
        JSort<TUser> ascOnly = s.filter(JOrder::isAscending);
        org.junit.jupiter.api.Assertions.assertEquals(1, ascOnly.getOrders().size());
        org.junit.jupiter.api.Assertions.assertEquals(
                "username", ascOnly.getOrders().get(0).getPropertyRef().propertyName());
    }

    // ====================================================================
    //  JSort — toSpringSort (output only)
    // ====================================================================

    @Test
    void jSort_toSpringSort_unsorted() {
        Sort springSort = JSort.unsorted().toSpringSort();
        org.junit.jupiter.api.Assertions.assertTrue(springSort.isUnsorted());
    }

    @Test
    void jSort_toSpringSort_withOrders() {
        Sort springSort = JSort.byAsc(TUser::getUsername).andDesc(TUser::getAge).toSpringSort();
        org.junit.jupiter.api.Assertions.assertTrue(springSort.isSorted());
        org.junit.jupiter.api.Assertions.assertNotNull(springSort.getOrderFor("username"));
        org.junit.jupiter.api.Assertions.assertTrue(
                springSort.getOrderFor("username").isAscending());
        org.junit.jupiter.api.Assertions.assertTrue(
                springSort.getOrderFor("age").isDescending());
    }

    @Test
    void jSort_toSpringSort_propagatesIgnoreCase() {
        Sort springSort = JSort.by(JOrder.asc(TUser::getUsername).ignoreCase()).toSpringSort();
        org.junit.jupiter.api.Assertions.assertTrue(
                springSort.getOrderFor("username").isIgnoreCase());
    }

    @Test
    void jSort_toSpringSort_propagatesNullHandling() {
        Sort springSort = JSort.by(JOrder.asc(TUser::getUsername).nullsFirst()).toSpringSort();
        org.junit.jupiter.api.Assertions.assertEquals(
                Sort.NullHandling.NULLS_FIRST,
                springSort.getOrderFor("username").getNullHandling());
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
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("LOWER(t.username) ASC"));
    }

    @Test
    void sqlRenderer_orderBy_nullsFirst() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .orderBy(JSort.by(JOrder.asc(TUser::getUsername).nullsFirst()))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("NULLS FIRST"));
    }

    @Test
    void sqlRenderer_orderBy_nullsLast() {
        SelectSpec<TUser, UserDto> spec = SelectBuilder.from(TUser.class)
                .select(TUser::getId, TUser::getUsername)
                .orderBy(JSort.by(JOrder.desc(TUser::getUsername).nullsLast()))
                .mapTo(UserDto.class);

        RenderedSql rendered = SqlRenderer.renderSelect(spec);
        org.junit.jupiter.api.Assertions.assertTrue(rendered.getSql().contains("NULLS LAST"));
    }

    // ====================================================================
    //  JPageable — factories
    // ====================================================================

    @Test
    void jPageable_of_pageAndSize() {
        JPageable<TUser> p = JPageable.of(2, 10);
        org.junit.jupiter.api.Assertions.assertEquals(2, p.getPageNumber());
        org.junit.jupiter.api.Assertions.assertEquals(10, p.getPageSize());
        org.junit.jupiter.api.Assertions.assertTrue(p.getSort().isUnsorted());
        org.junit.jupiter.api.Assertions.assertEquals(20L, p.getOffset());
    }

    @Test
    void jPageable_of_withSort() {
        JSort<TUser> sort = JSort.byAsc(TUser::getUsername);
        JPageable<TUser> p = JPageable.of(1, 5, sort);
        org.junit.jupiter.api.Assertions.assertTrue(p.getSort().isSorted());
    }

    @Test
    void jPageable_of_withDirectionAndProps() {
        JPageable<TUser> p = JPageable.of(0, 10, JOrder.Direction.DESC, TUser::getUsername, TUser::getAge);
        org.junit.jupiter.api.Assertions.assertEquals(2, p.getSort().getOrders().size());
        org.junit.jupiter.api.Assertions.assertTrue(
                p.getSort().getOrders().stream().allMatch(JOrder::isDescending));
    }

    @Test
    void jPageable_ofSize_createsFirstPage() {
        JPageable<TUser> p = JPageable.ofSize(15);
        org.junit.jupiter.api.Assertions.assertEquals(0, p.getPageNumber());
        org.junit.jupiter.api.Assertions.assertEquals(15, p.getPageSize());
        org.junit.jupiter.api.Assertions.assertTrue(p.getSort().isUnsorted());
    }

    @Test
    void jPageable_negativePage_throws() {
        Throwable assertionThrown1 = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> JPageable.of(-1, 10));
        org.junit.jupiter.api.Assertions.assertTrue(
                assertionThrown1.getMessage().contains("negative"));
    }

    @Test
    void jPageable_zeroSize_throws() {
        Throwable assertionThrown2 =
                org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> JPageable.of(0, 0));
        org.junit.jupiter.api.Assertions.assertTrue(
                assertionThrown2.getMessage().contains("positive"));
    }

    @Test
    void jPageable_unpaged_withoutSort() {
        JPageable<TUser> pageable = JPageable.unpaged();

        org.junit.jupiter.api.Assertions.assertTrue(pageable.isUnpaged());
        org.junit.jupiter.api.Assertions.assertFalse(pageable.isPaged());
        org.junit.jupiter.api.Assertions.assertTrue(pageable.getSort().isUnsorted());
        Throwable assertionThrown3 = org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class, pageable::getPageNumber);
        Throwable assertionThrown4 = org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class, pageable::getPageSize);
        Throwable assertionThrown5 =
                org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, pageable::getOffset);
    }

    @Test
    void jPageable_unpaged_withSort_preservesSortInSpringAdapter() {
        JSort<TUser> sort = JSort.byDesc(TUser::getUsername);
        JPageable<TUser> pageable = JPageable.unpaged(sort);

        org.junit.jupiter.api.Assertions.assertTrue(pageable.isUnpaged());
        org.junit.jupiter.api.Assertions.assertSame(sort, pageable.getSort());
        org.junit.jupiter.api.Assertions.assertTrue(pageable.toSpringPageable().isUnpaged());
        org.junit.jupiter.api.Assertions.assertTrue(
                pageable.toSpringPageable().getSort().isSorted());
        org.junit.jupiter.api.Assertions.assertTrue(
                pageable.withSort(JSort.byAsc(TUser::getAge)).isUnpaged());
    }

    // ====================================================================
    //  JPageable — accessor aliases
    // ====================================================================

    @Test
    void jPageable_getPage_and_getPageNumber_match() {
        JPageable<TUser> p = JPageable.of(3, 10);
        org.junit.jupiter.api.Assertions.assertEquals(p.getPageNumber(), p.getPage());
        org.junit.jupiter.api.Assertions.assertEquals(3, p.getPage());
    }

    @Test
    void jPageable_getSize_and_getPageSize_match() {
        JPageable<TUser> p = JPageable.of(0, 7);
        org.junit.jupiter.api.Assertions.assertEquals(p.getPageSize(), p.getSize());
        org.junit.jupiter.api.Assertions.assertEquals(7, p.getSize());
    }

    @Test
    void jPageable_offset_and_getOffset_match() {
        JPageable<TUser> p = JPageable.of(3, 10);
        org.junit.jupiter.api.Assertions.assertEquals(p.getOffset(), p.offset());
        org.junit.jupiter.api.Assertions.assertEquals(30L, p.offset());
    }

    // ====================================================================
    //  JPageable — navigation
    // ====================================================================

    @Test
    void jPageable_next_incrementsPage() {
        JPageable<TUser> p = JPageable.of(1, 10);
        JPageable<TUser> next = p.next();
        org.junit.jupiter.api.Assertions.assertEquals(2, next.getPageNumber());
        org.junit.jupiter.api.Assertions.assertEquals(10, next.getPageSize());
    }

    @Test
    void jPageable_previous_decrementPage() {
        JPageable<TUser> p = JPageable.of(2, 10);
        JPageable<TUser> prev = p.previous();
        org.junit.jupiter.api.Assertions.assertEquals(1, prev.getPageNumber());
    }

    @Test
    void jPageable_previous_onFirstPage_throws() {
        JPageable<TUser> p = JPageable.of(0, 10);
        Throwable assertionThrown6 =
                org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, p::previous);
        org.junit.jupiter.api.Assertions.assertTrue(
                assertionThrown6.getMessage().contains("previousOrFirst"));
    }

    @Test
    void jPageable_previousOrFirst_onFirstPage_returnsSelf() {
        JPageable<TUser> p = JPageable.of(0, 10);
        JPageable<TUser> result = p.previousOrFirst();
        org.junit.jupiter.api.Assertions.assertEquals(0, result.getPageNumber());
    }

    @Test
    void jPageable_previousOrFirst_onLaterPage_decrementsPage() {
        JPageable<TUser> p = JPageable.of(3, 10);
        JPageable<TUser> result = p.previousOrFirst();
        org.junit.jupiter.api.Assertions.assertEquals(2, result.getPageNumber());
    }

    @Test
    void jPageable_first_returnsPageZero() {
        JPageable<TUser> p = JPageable.of(5, 10);
        JPageable<TUser> first = p.first();
        org.junit.jupiter.api.Assertions.assertEquals(0, first.getPageNumber());
        org.junit.jupiter.api.Assertions.assertEquals(10, first.getPageSize());
    }

    @Test
    void jPageable_first_onFirstPage_returnsSelf() {
        JPageable<TUser> p = JPageable.of(0, 10);
        org.junit.jupiter.api.Assertions.assertSame(p, p.first());
    }

    @Test
    void jPageable_withPage_createsNewPageable() {
        JPageable<TUser> p = JPageable.of(0, 10);
        JPageable<TUser> p5 = p.withPage(5);
        org.junit.jupiter.api.Assertions.assertEquals(5, p5.getPageNumber());
        org.junit.jupiter.api.Assertions.assertEquals(10, p5.getPageSize());
    }

    @Test
    void jPageable_withPage_samePage_returnsSelf() {
        JPageable<TUser> p = JPageable.of(2, 10);
        org.junit.jupiter.api.Assertions.assertSame(p, p.withPage(2));
    }

    @Test
    void jPageable_hasPrevious() {
        org.junit.jupiter.api.Assertions.assertFalse(JPageable.of(0, 10).hasPrevious());
        org.junit.jupiter.api.Assertions.assertTrue(JPageable.of(1, 10).hasPrevious());
    }

    @Test
    void jPageable_isPaged_isUnpaged() {
        JPageable<TUser> p = JPageable.of(0, 10);
        org.junit.jupiter.api.Assertions.assertTrue(p.isPaged());
        org.junit.jupiter.api.Assertions.assertFalse(p.isUnpaged());
    }

    // ====================================================================
    //  JPageable — getSortOr
    // ====================================================================

    @Test
    void jPageable_getSortOr_unsorted_returnsDefault() {
        JPageable<TUser> p = JPageable.of(0, 10);
        JSort<TUser> defaultSort = JSort.byAsc(TUser::getUsername);
        org.junit.jupiter.api.Assertions.assertSame(defaultSort, p.getSortOr(defaultSort));
    }

    @Test
    void jPageable_getSortOr_withSort_returnsOwn() {
        JSort<TUser> mySort = JSort.byDesc(TUser::getAge);
        JPageable<TUser> p = JPageable.of(0, 10, mySort);
        JSort<TUser> defaultSort = JSort.byAsc(TUser::getUsername);
        org.junit.jupiter.api.Assertions.assertSame(mySort, p.getSortOr(defaultSort));
    }

    // ====================================================================
    //  JPageable — withSort
    // ====================================================================

    @Test
    void jPageable_withSort_jsort() {
        JPageable<TUser> p = JPageable.of(1, 10);
        JSort<TUser> newSort = JSort.byDesc(TUser::getAge);
        JPageable<TUser> updated = p.withSort(newSort);
        org.junit.jupiter.api.Assertions.assertEquals(1, updated.getPageNumber());
        org.junit.jupiter.api.Assertions.assertSame(newSort, updated.getSort());
    }

    @Test
    void jPageable_withSort_directionAndProps() {
        JPageable<TUser> p = JPageable.of(0, 10);
        JPageable<TUser> updated = p.withSort(JOrder.Direction.DESC, TUser::getUsername);
        org.junit.jupiter.api.Assertions.assertTrue(
                updated.getSort().getOrders().get(0).isDescending());
    }

    // ====================================================================
    //  JPageable — toSpringPageable (output only)
    // ====================================================================

    @Test
    void jPageable_toSpringPageable_correctValues() {
        JPageable<TUser> p = JPageable.of(2, 5, JSort.byAsc(TUser::getUsername));
        org.springframework.data.domain.Pageable spring = p.toSpringPageable();
        org.junit.jupiter.api.Assertions.assertEquals(2, spring.getPageNumber());
        org.junit.jupiter.api.Assertions.assertEquals(5, spring.getPageSize());
        org.junit.jupiter.api.Assertions.assertTrue(spring.getSort().isSorted());
    }
}
