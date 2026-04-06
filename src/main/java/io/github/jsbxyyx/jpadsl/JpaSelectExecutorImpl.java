package io.github.jsbxyyx.jpadsl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.metamodel.SingularAttribute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of {@link JpaSelectExecutor}.
 *
 * <p>Instantiated by {@link JpaSelectFragmentsContributor} and contributed as a
 * repository fragment to every Spring Data JPA repository that extends
 * {@link JpaSelectExecutor}.
 *
 * <p>Uses the JPA Criteria API internally to build:
 * <ul>
 *   <li>a data query with {@code cb.construct(dtoClass, ...)} constructor expression</li>
 *   <li>a count query ({@code cb.count(root)}) for pagination total</li>
 * </ul>
 *
 * @param <T> the root entity type
 */
public class JpaSelectExecutorImpl<T> implements JpaSelectExecutor<T> {

    private final EntityManager entityManager;

    public JpaSelectExecutorImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public <R> List<R> select(SelectSpec<T, R> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<R> cq = cb.createQuery(spec.getDtoClass());
        Root<T> root = cq.from(spec.getEntityClass());

        cq.select(buildConstruct(cb, cq, root, spec));
        applyWhere(cb, cq, root, spec.getWhereSpec());

        return entityManager.createQuery(cq).getResultList();
    }

    @Override
    public <R> Page<R> selectPage(SelectSpec<T, R> spec, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        // --- data query ---
        CriteriaQuery<R> cq = cb.createQuery(spec.getDtoClass());
        Root<T> root = cq.from(spec.getEntityClass());

        cq.select(buildConstruct(cb, cq, root, spec));
        applyWhere(cb, cq, root, spec.getWhereSpec());
        applySort(cb, cq, root, pageable.getSort());

        TypedQuery<R> dataQuery = entityManager.createQuery(cq);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());
        List<R> content = dataQuery.getResultList();

        // --- count query ---
        CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
        Root<T> countRoot = countCq.from(spec.getEntityClass());
        countCq.select(cb.count(countRoot));
        applyWhere(cb, countCq, countRoot, spec.getWhereSpec());

        Long total = entityManager.createQuery(countCq).getSingleResult();

        return new PageImpl<>(content, pageable, total);
    }

    private <R> jakarta.persistence.criteria.CompoundSelection<R> buildConstruct(
            CriteriaBuilder cb,
            CriteriaQuery<R> cq,
            Root<T> root,
            SelectSpec<T, R> spec) {

        List<Selection<?>> selections = new ArrayList<>();
        for (SingularAttribute<? super T, ?> attr : spec.getSelectedAttrs()) {
            selections.add(root.get(attr.getName()));
        }
        return cb.construct(spec.getDtoClass(), selections.toArray(new Selection[0]));
    }

    private <X> void applyWhere(CriteriaBuilder cb,
                                CriteriaQuery<X> cq,
                                Root<T> root,
                                Specification<T> whereSpec) {
        if (whereSpec != null) {
            Predicate predicate = whereSpec.toPredicate(root, cq, cb);
            if (predicate != null) {
                cq.where(predicate);
            }
        }
    }

    private void applySort(CriteriaBuilder cb,
                           CriteriaQuery<?> cq,
                           Root<T> root,
                           Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return;
        }
        List<Order> orders = new ArrayList<>();
        for (Sort.Order sortOrder : sort) {
            if (sortOrder.isAscending()) {
                orders.add(cb.asc(root.get(sortOrder.getProperty())));
            } else {
                orders.add(cb.desc(root.get(sortOrder.getProperty())));
            }
        }
        if (!orders.isEmpty()) {
            cq.orderBy(orders);
        }
    }
}
