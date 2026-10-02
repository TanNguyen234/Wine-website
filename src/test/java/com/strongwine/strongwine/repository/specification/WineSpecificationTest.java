package com.strongwine.strongwine.repository.specification;

import com.strongwine.strongwine.dto.WineSearchCriteria;
import com.strongwine.strongwine.entity.Wine;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

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
}
