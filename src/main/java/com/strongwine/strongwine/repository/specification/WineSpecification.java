package com.strongwine.strongwine.repository.specification;

import com.strongwine.strongwine.dto.WineSearchCriteria;
import com.strongwine.strongwine.entity.Inventory;
import com.strongwine.strongwine.entity.Warehouse;
import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.util.AddressTextUtils;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class WineSpecification {

    private WineSpecification() {
    }

    public static Specification<Wine> build(WineSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Luôn loại trừ sản phẩm đã bị xóa
            predicates.add(cb.isFalse(root.get("deleted")));

            if (criteria == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }

            // Từ khóa tìm kiếm (Tên hoặc Mô tả, chống NPE khi description null)
            String keyword = AddressTextUtils.normalizeText(criteria.getKeyword());
            if (keyword != null && !keyword.isEmpty()) {
                String pattern = "%" + keyword.toLowerCase(Locale.ROOT) + "%";
                Predicate nameLike = cb.like(cb.lower(cb.coalesce(root.get("name"), "")), pattern);
                Predicate descLike = cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern);
                predicates.add(cb.or(nameLike, descLike));
            }

            // Danh sách loại rượu (Red, White, Rose, Sparkling, Traditional) không phân biệt hoa thường/dấu
            if (criteria.getTypes() != null && !criteria.getTypes().isEmpty()) {
                List<String> normalizedTypes = criteria.getTypes().stream()
                        .map(WineSpecification::normalizeTypeFilterValue)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
                if (!normalizedTypes.isEmpty()) {
                    predicates.add(cb.lower(cb.coalesce(root.get("type"), "")).in(normalizedTypes));
                }
            }

            // Khoảng giá (DECIMAL(18,2))
            if (criteria.getMinPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), criteria.getMinPrice()));
            }
            if (criteria.getMaxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), criteria.getMaxPrice()));
            }

            // Xuất xứ / Quốc gia
            if (criteria.getCountries() != null && !criteria.getCountries().isEmpty()) {
                List<String> normalizedCountries = criteria.getCountries().stream()
                        .map(AddressTextUtils::normalizeText)
                        .filter(country -> country != null && !country.isEmpty())
                        .map(country -> country.toLowerCase(Locale.ROOT))
                        .distinct()
                        .toList();
                if (!normalizedCountries.isEmpty()) {
                    predicates.add(cb.lower(cb.coalesce(root.get("country"), "")).in(normalizedCountries));
                }
            }

            // Năm sản xuất (Vintage)
            if (criteria.getVintageYears() != null && !criteria.getVintageYears().isEmpty()) {
                List<Integer> validYears = criteria.getVintageYears().stream()
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
                if (!validYears.isEmpty()) {
                    predicates.add(root.get("year").in(validYears));
                }
            }

            // Danh mục
            if (criteria.getCategoryId() != null) {
                predicates.add(cb.equal(root.get("category").get("id"), criteria.getCategoryId()));
            }

            // Chỉ lọc sản phẩm còn tồn kho khả dụng tại các kho đang hoạt động
            if (Boolean.TRUE.equals(criteria.getInStockOnly()) && query != null) {
                Subquery<Long> stockSubquery = query.subquery(Long.class);
                Root<Inventory> invRoot = stockSubquery.from(Inventory.class);
                Join<Inventory, Warehouse> whJoin = invRoot.join("warehouse");
                Expression<Integer> availableStock = cb.diff(invRoot.get("currentQuantity"), invRoot.get("reservedQuantity"));
                stockSubquery.select(invRoot.get("id")).where(
                        cb.equal(invRoot.get("wine").get("id"), root.get("id")),
                        cb.isTrue(whJoin.get("active")),
                        cb.greaterThan(availableStock, 0)
                );
                predicates.add(cb.exists(stockSubquery));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String normalizeTypeFilterValue(String rawType) {
        String normalized = AddressTextUtils.normalizeText(rawType);
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }
        String lowerAscii = AddressTextUtils.stripAccents(normalized).toLowerCase(Locale.ROOT);
        return switch (lowerAscii) {
            case "red", "vang do" -> "red";
            case "white", "vang trang" -> "white";
            case "rose", "vang hong" -> "rose";
            case "sparkling", "vang sui" -> "sparkling";
            case "traditional", "ruou truyen thong", "traditional wine" -> "traditional";
            default -> lowerAscii;
        };
    }
}
