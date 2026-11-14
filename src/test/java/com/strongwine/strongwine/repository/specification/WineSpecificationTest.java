package com.strongwine.strongwine.repository.specification;

import com.strongwine.strongwine.dto.WineSearchCriteria;
import com.strongwine.strongwine.entity.Inventory;
import com.strongwine.strongwine.entity.Warehouse;
import com.strongwine.strongwine.entity.Wine;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WineSpecificationTest {

    @Test
    void testBuildEmptyCriteriaReturnsEmptySpec() {
        WineSearchCriteria criteria = new WineSearchCriteria();
        Specification<Wine> spec = WineSpecification.build(criteria);
        assertNotNull(spec);
    }

    @Test
    void testBuildCompoundCriteria() {
        WineSearchCriteria criteria = WineSearchCriteria.builder()
                .keyword("Bordeaux")
                .types(List.of("Red"))
                .minPrice(BigDecimal.valueOf(1000000))
                .maxPrice(BigDecimal.valueOf(5000000))
                .countries(List.of("Pháp"))
                .vintageYears(List.of(2018, 2020))
                .build();

        Specification<Wine> spec = WineSpecification.build(criteria);
        assertNotNull(spec);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    @DisplayName("Null criteria still enforces deleted=false predicate without NPE")
    void testNullCriteriaFiltersDeletedFalse() {
        Root<Wine> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);

        Path<Boolean> deletedPath = mock(Path.class);
        Predicate deletedFalse = mock(Predicate.class);
        Predicate finalAnd = mock(Predicate.class);

        when(root.<Boolean>get("deleted")).thenReturn(deletedPath);
        when(cb.isFalse(deletedPath)).thenReturn(deletedFalse);
        when(cb.and(any(Predicate[].class))).thenReturn(finalAnd);

        Predicate result = WineSpecification.build(null).toPredicate(root, query, cb);

        assertSame(finalAnd, result);
        verify(cb).isFalse(deletedPath);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    @DisplayName("Normalizes Traditional & other wine types case-insensitively, ignores blank filters, and handles DECIMAL(18,2) price & coalesce keyword")
    void testTraditionalTypeKeywordCoalesceAndDecimal18Bounds() {
        Root<Wine> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);

        Path<Boolean> deletedPath = mock(Path.class);
        Path<String> namePath = mock(Path.class);
        Path<String> descPath = mock(Path.class);
        Path<String> typePath = mock(Path.class);
        Path<BigDecimal> pricePath = mock(Path.class);
        Path<String> countryPath = mock(Path.class);
        Path<Integer> yearPath = mock(Path.class);
        Path categoryPath = mock(Path.class);
        Path<Long> categoryIdPath = mock(Path.class);

        Expression<String> coalesceName = mock(Expression.class);
        Expression<String> coalesceDesc = mock(Expression.class);
        Expression<String> coalesceType = mock(Expression.class);
        Expression<String> coalesceCountry = mock(Expression.class);

        Expression<String> lowerName = mock(Expression.class);
        Expression<String> lowerDesc = mock(Expression.class);
        Expression<String> lowerType = mock(Expression.class);
        Expression<String> lowerCountry = mock(Expression.class);

        Predicate p = mock(Predicate.class);

        when(root.<Boolean>get("deleted")).thenReturn(deletedPath);
        when(root.<String>get("name")).thenReturn(namePath);
        when(root.<String>get("description")).thenReturn(descPath);
        when(root.<String>get("type")).thenReturn(typePath);
        when(root.<BigDecimal>get("price")).thenReturn(pricePath);
        when(root.<String>get("country")).thenReturn(countryPath);
        when(root.<Integer>get("year")).thenReturn(yearPath);
        when(root.get("category")).thenReturn(categoryPath);
        when(categoryPath.<Long>get("id")).thenReturn(categoryIdPath);

        when(cb.coalesce(namePath, "")).thenReturn(coalesceName);
        when(cb.coalesce(descPath, "")).thenReturn(coalesceDesc);
        when(cb.coalesce(typePath, "")).thenReturn(coalesceType);
        when(cb.coalesce(countryPath, "")).thenReturn(coalesceCountry);

        when(cb.lower(coalesceName)).thenReturn(lowerName);
        when(cb.lower(coalesceDesc)).thenReturn(lowerDesc);
        when(cb.lower(coalesceType)).thenReturn(lowerType);
        when(cb.lower(coalesceCountry)).thenReturn(lowerCountry);

        when(cb.isFalse(deletedPath)).thenReturn(p);
        when(cb.like(any(Expression.class), anyString())).thenReturn(p);
        when(cb.or(any(Predicate.class), any(Predicate.class))).thenReturn(p);
        when(lowerType.in(any(Collection.class))).thenReturn(p);
        when(lowerCountry.in(any(Collection.class))).thenReturn(p);
        when(yearPath.in(any(Collection.class))).thenReturn(p);
        when(cb.greaterThanOrEqualTo(eq(pricePath), any(BigDecimal.class))).thenReturn(p);
        when(cb.lessThanOrEqualTo(eq(pricePath), any(BigDecimal.class))).thenReturn(p);
        when(cb.equal(categoryIdPath, 5L)).thenReturn(p);
        when(cb.and(any(Predicate[].class))).thenReturn(p);

        BigDecimal min18 = new BigDecimal("125000000.50");
        BigDecimal max18 = new BigDecimal("950000000.00");

        WineSearchCriteria criteria = WineSearchCriteria.builder()
                .keyword("  Rượu   Mơ  ")
                .types(Arrays.asList("  traditional ", "Rosé", "RED", "", null))
                .minPrice(min18)
                .maxPrice(max18)
                .countries(Arrays.asList("  Vietnam ", null, "  "))
                .vintageYears(Arrays.asList(2019, null, 2021))
                .categoryId(5L)
                .inStockOnly(false)
                .build();

        Predicate result = WineSpecification.build(criteria).toPredicate(root, query, cb);

        assertSame(p, result);
        verify(cb).coalesce(namePath, "");
        verify(cb).coalesce(descPath, "");
        verify(cb).like(lowerName, "%rượu mơ%");
        verify(cb).like(lowerDesc, "%rượu mơ%");
        verify(lowerType).in(argThat((Collection<String> c) ->
                c.containsAll(List.of("traditional", "rose", "red")) && !c.contains("")));
        verify(cb).greaterThanOrEqualTo(pricePath, min18);
        verify(cb).lessThanOrEqualTo(pricePath, max18);
        verify(lowerCountry).in(argThat((Collection<String> c) -> c.equals(List.of("vietnam"))));
        verify(yearPath).in(argThat((Collection<Integer> c) -> c.equals(List.of(2019, 2021))));
        verify(cb).equal(categoryIdPath, 5L);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    @DisplayName("inStockOnly=true builds active-warehouse available-stock EXISTS subquery")
    void testInStockOnlyUsesActiveWarehouseSubquery() {
        Root<Wine> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);

        Path<Boolean> deletedPath = mock(Path.class);
        Path<Long> wineIdPath = mock(Path.class);
        Subquery<Long> subquery = mock(Subquery.class);
        Root<Inventory> invRoot = mock(Root.class);
        Join<Inventory, Warehouse> whJoin = mock(Join.class);
        Path<Long> invIdPath = mock(Path.class);
        Path invWinePath = mock(Path.class);
        Path<Long> invWineIdPath = mock(Path.class);
        Path<Boolean> whActivePath = mock(Path.class);
        Path<Integer> currentQtyPath = mock(Path.class);
        Path<Integer> reservedQtyPath = mock(Path.class);
        Expression<Integer> availableExpr = mock(Expression.class);
        Predicate p = mock(Predicate.class);

        when(root.<Boolean>get("deleted")).thenReturn(deletedPath);
        when(root.<Long>get("id")).thenReturn(wineIdPath);
        when(query.subquery(Long.class)).thenReturn(subquery);
        when(subquery.from(Inventory.class)).thenReturn(invRoot);
        when(invRoot.<Inventory, Warehouse>join("warehouse")).thenReturn(whJoin);
        when(invRoot.<Long>get("id")).thenReturn(invIdPath);
        when(invRoot.get("wine")).thenReturn(invWinePath);
        when(invWinePath.<Long>get("id")).thenReturn(invWineIdPath);
        when(whJoin.<Boolean>get("active")).thenReturn(whActivePath);
        when(invRoot.<Integer>get("currentQuantity")).thenReturn(currentQtyPath);
        when(invRoot.<Integer>get("reservedQuantity")).thenReturn(reservedQtyPath);
        when(cb.diff(currentQtyPath, reservedQtyPath)).thenReturn(availableExpr);

        when(subquery.select(invIdPath)).thenReturn(subquery);
        when(subquery.where(any(Predicate[].class))).thenReturn(subquery);
        when(cb.isFalse(deletedPath)).thenReturn(p);
        when(cb.equal(invWineIdPath, wineIdPath)).thenReturn(p);
        when(cb.isTrue(whActivePath)).thenReturn(p);
        when(cb.greaterThan(eq(availableExpr), anyInt())).thenReturn(p);
        when(cb.exists(subquery)).thenReturn(p);
        when(cb.and(any(Predicate[].class))).thenReturn(p);

        WineSearchCriteria criteria = WineSearchCriteria.builder()
                .types(List.of("Rượu Truyền Thống"))
                .inStockOnly(true)
                .build();

        Path<String> typePath = mock(Path.class);
        Expression<String> coalesceType = mock(Expression.class);
        Expression<String> lowerType = mock(Expression.class);
        when(root.<String>get("type")).thenReturn(typePath);
        when(cb.coalesce(typePath, "")).thenReturn(coalesceType);
        when(cb.lower(coalesceType)).thenReturn(lowerType);
        when(lowerType.in(any(Collection.class))).thenReturn(p);

        Predicate result = WineSpecification.build(criteria).toPredicate(root, query, cb);

        assertSame(p, result);
        verify(lowerType).in(argThat((Collection<String> c) -> c.contains("traditional")));
        verify(cb).isTrue(whActivePath);
        verify(cb).diff(currentQtyPath, reservedQtyPath);
        verify(cb).greaterThan(availableExpr, 0);
        verify(cb).exists(subquery);
    }
}

