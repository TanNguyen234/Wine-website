package com.strongwine.strongwine.repository.specification;

import com.strongwine.strongwine.dto.WineSearchCriteria;
import com.strongwine.strongwine.entity.Wine;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class WineSpecification {

    public static Specification<Wine> build(WineSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Luôn loại trừ sản phẩm đã bị xóa
            predicates.add(cb.isFalse(root.get("deleted")));

            if (criteria == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }

            // Từ khóa tìm kiếm (Tên hoặc Mô tả)
            if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
                String pattern = "%" + criteria.getKeyword().trim().toLowerCase() + "%";
                Predicate nameLike = cb.like(cb.lower(root.get("name")), pattern);
                Predicate descLike = cb.like(cb.lower(root.get("description")), pattern);
                predicates.add(cb.or(nameLike, descLike));
            }

            // Danh sách loại rượu (Red, White, Rose, Sparkling)
            if (criteria.getTypes() != null && !criteria.getTypes().isEmpty()) {
                predicates.add(root.get("type").in(criteria.getTypes()));
            }

            // Khoảng giá
            if (criteria.getMinPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), criteria.getMinPrice()));
            }
            if (criteria.getMaxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), criteria.getMaxPrice()));
            }

            // Xuất xứ / Quốc gia
            if (criteria.getCountries() != null && !criteria.getCountries().isEmpty()) {
                predicates.add(root.get("country").in(criteria.getCountries()));
            }

            // Năm sản xuất (Vintage)
            if (criteria.getVintageYears() != null && !criteria.getVintageYears().isEmpty()) {
                predicates.add(root.get("year").in(criteria.getVintageYears()));
            }

            // Danh mục
            if (criteria.getCategoryId() != null) {
                predicates.add(cb.equal(root.get("category").get("id"), criteria.getCategoryId()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
